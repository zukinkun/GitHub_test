# TapGame 設計書

## 1. 概要
10秒間に「TAP!」ボタンを何回押せるかを競うAndroidアプリ。
初版の単純なタップカウンタに、ハイスコア保存・カウントダウン・コンボ・演出・結果カードを加えて改良した。

| 項目 | 内容 |
|---|---|
| パッケージ | `com.example.tapgame` |
| 言語 / UI | Kotlin / XML レイアウト + ViewBinding（Material3） |
| SDK | minSdk 24 / targetSdk 36 / compileSdk 36 |
| ビルド | AGP 9.0.0, Gradle 9.1 |
| 主な依存 | appcompat, material, lifecycle-viewmodel/runtime-ktx 2.9.4, coroutines-test 1.10.2（テスト） |

## 2. 機能一覧
| 機能 | 内容 |
|---|---|
| 3・2・1・GO! | 開始前にカウントダウンを表示（フライングタップ防止） |
| 時間バー | 残り時間を横バーで表示。緑 → 黄(50%以下) → 赤(25%以下)。残り3秒で数字が点滅 |
| タップ演出 | ボタンが縮む / 振動 / 押した位置に「+1」が浮かんで消える |
| コンボ | 前回タップから500ms以内の連打で加算。3コンボ以上で「×N COMBO!」を表示し、数に応じて文字が大きく・色が派手になる（最大20で頭打ち） |
| スコア演出 | 加算のたびに数字が跳ねる |
| 背景切替 | 待機中は寒色、プレイ中は暖色へなめらかに切り替わる |
| 結果カード | スコア、ハイスコア、NEW RECORD、平均タップ速度(回/秒)、称号、「もう一度」 |
| ハイスコア | `SharedPreferences` に保存 |
| 称号 | 〜29 初心者 / 30〜49 見習い / 50〜69 達人 / 70〜 神速 |
| 中断 | アプリがバックグラウンドに回ると、進行中のゲームを破棄しスコアは無効 |
| 画面回転 | 回転してもゲーム状態を保持（中断しない） |

## 3. 画面フロー
```
Idle ──スタート──▶ Countdown(3,2,1,GO!) ──▶ Playing(10秒) ──▶ Finished(結果カード)
 ▲                        │                       │                    │
 │                        └──── 中断(onStop) ──────┘                    │
 └──────────────── 中断時は Idle に戻る ◀──────          「もう一度」で Countdown へ
```

## 4. アーキテクチャ
```
MainActivity ........ 描画・アニメーション・振動・タッチ受付（UIのみ）
   │  StateFlow を collect / onTap・start・interrupt を呼ぶ
GameViewModel ....... フェーズ遷移・タイマー・スコア・コンボ判定
   │
ScoreStore (interface) ── SharedPrefsScoreStore（ハイスコアの読み書き）
```

### 4.1 クラス構成
| ファイル | 役割 |
|---|---|
| `MainActivity.kt` | `GameUiState` を描画する。ロジックは持たない |
| `GameViewModel.kt` | ゲームロジック。`StateFlow<GameUiState>` を公開 |
| `GameUiState.kt` | 画面状態 `GameUiState`、`Phase`、`Rank` |
| `ScoreRepository.kt` | `ScoreStore` インターフェースと `SharedPrefsScoreStore` |
| `FloatingTextView.kt` | 「+1」浮遊演出のヘルパー（`FloatingText.show`） |

### 4.2 GameUiState
| フィールド | 意味 |
|---|---|
| `phase` | `Idle` / `Countdown` / `Playing` / `Finished` |
| `countdown` | 3,2,1、0 は「GO!」 |
| `score` | 現在のタップ数 |
| `remainingMs` | 残り時間(ms)。`progress`（1.0→0.0）は派生値 |
| `combo` | 現在のコンボ数 |
| `highScore` / `isNewRecord` | ハイスコアと更新フラグ |
| `tapsPerSec` | 平均タップ速度（結果表示用） |
| `rank` | 称号 |
| `interrupted` | 中断直後だけ true（トースト表示用） |

### 4.3 GameViewModel の仕様
- **タイマー**: `viewModelScope` の coroutine。残り時間は「開始時刻との差」で計算し、`delay` の誤差が積もらない（50msごとに更新）。
- **時計の注入**: コンストラクタで `now: () -> Long` を受け取る（既定は `SystemClock.elapsedRealtime`）。テストでは仮想時間を渡す。
- **start()**: Idle / Finished のときだけ受け付ける。カウントダウン → Playing へ進む。
- **onTap()**: Playing のときだけ加算。前回タップから `COMBO_WINDOW_MS`(500ms) 以内なら `combo+1`、それ以外は 1。
- **finish()**: 10秒経過で呼ばれる。`score > 保存済みハイスコア` のときだけ新記録として保存（0点は新記録にならない）。
- **interrupt()**: Countdown / Playing のときだけ有効。ジョブを取り消し、スコアを破棄して Idle に戻す。
- **回転対応**: `MainActivity.onStop` は `isChangingConfigurations` が true のとき中断しない。ViewModel が状態を保持する。

### 4.4 定数
| 名前 | 値 |
|---|---|
| `GAME_DURATION_MS` | 10,000 |
| `COMBO_WINDOW_MS` | 500 |
| カウントダウン開始値 | 3（GO! の表示 500ms） |
| コンボ表示の下限 / 見た目の上限 | 3 / 20 |
| 称号の閾値 | `Rank` の `minScore`（0/30/50/70） |

## 5. UI 構成（activity_main.xml）
`FrameLayout`（背景 `bg_idle`）に下記を重ねる。
1. `bgPlaying` … プレイ中の暖色グラデーション。alpha で切替
2. `ScrollView` … 学校名、イベント名、時間バー、残り時間、スコア、コンボ、TAPボタン、ヒント、スタートボタン
3. `effectLayer` … 「+1」演出用（タッチ透過）
4. `countdownText` … 巨大カウントダウン文字
5. `resultOverlay` … スクリム + 結果カード

システムバーの inset はルートの padding として適用する（Android 15+ の edge-to-edge 対応）。

## 6. リソース
- 文字列はすべて `strings.xml`（直書きなし）。
- 色は `colors.xml`、背景グラデーションは `drawable/bg_idle.xml` と `bg_playing.xml`。

## 7. テスト
`app/src/test/.../GameViewModelTest.kt`（JUnit4 + kotlinx-coroutines-test、仮想時間で実行）。

| テスト | 検証内容 |
|---|---|
| start_runsCountdownThenPlaying | カウントダウンから Playing への遷移 |
| tapsAreIgnoredOutsidePlaying | Idle / カウントダウン中のタップ無効 |
| fullGame_finishesWithScoreAndSavesNewRecord | 終了、スコア、新記録の保存、平均速度 |
| lowerScore_isNotNewRecord | 低スコアでハイスコアが変わらない |
| zeroScore_isNeverNewRecord | 0点は新記録にならない |
| combo_chainsWithinWindowAndResetsAfter | コンボの成立と途切れ |
| interrupt_discardsGameAndInvalidatesScore | 中断でスコア無効、保存されない |
| restart_afterFinish_resetsState | 再スタートで状態リセット、ハイスコア保持 |
| rank_boundaries | 称号の境界値 |

実行: `./gradlew testDebugUnitTest`（Android Studio 同梱 JDK が必要）。

## 8. 既知の制限・今後の拡張案
- UI（アニメーション・振動）の自動テストはなく、実機 / エミュレータでの目視確認が必要。
- 難易度（制限時間の選択）、サウンド、時間ごとのハイスコアは未実装。
- 横画面では `ScrollView` でスクロールして使う。
