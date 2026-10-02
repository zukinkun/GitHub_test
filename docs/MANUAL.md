# TapGame 説明書

## 1. これは何？
**10秒間に、オレンジの「TAP!」ボタンを何回押せるか**を競うゲームです。
新潟工業高校 IT工学科 ３年生 実習用のアプリです。

## 2. 遊び方
1. 「**ゲームスタート**」を押します。
2. 画面に「3 → 2 → 1 → GO!」と出ます。**GO! が出るまでは押してもカウントされません**。
3. 10秒間、できるだけ速く「**TAP!**」を連打します。
4. 時間になると**結果カード**が出ます。「**もう一度**」で再挑戦できます。

## 3. 画面の見方
| 表示 | 説明 |
|---|---|
| 上のバー | 残り時間。緑 → 黄 → 赤と変わり、赤になったら残りわずか |
| 残り ○.○ 秒 | 残り時間の数字。最後の3秒は点滅します |
| スコア | 今までに押した回数。増えるたびに数字が跳ねます |
| ×N COMBO! | 0.5秒以内に押し続けると増える連続数。3コンボ以上で表示され、大きく・派手な色になります |
| 押した場所の +1 | タップするたびに浮かんで消えます |
| 背景色 | 待機中は青、プレイ中はオレンジに変わります |

## 4. 結果カード
| 項目 | 説明 |
|---|---|
| ○ 回 | 今回のスコア |
| 🎉 NEW RECORD! | 今までのハイスコアを超えたときだけ表示 |
| ハイスコア | これまでの最高記録（端末に保存され、アプリを閉じても残ります） |
| 平均 ○.○ 回/秒 | 1秒あたりの平均タップ数 |
| 称号 | スコアで決まります（下表） |

| スコア | 称号 |
|---|---|
| 0〜29 | 初心者 |
| 30〜49 | 見習い |
| 50〜69 | 達人 |
| 70以上 | 神速 |

## 5. 知っておくこと
- プレイ中や カウントダウン中にホームボタンなどでアプリを離れると、**ゲームは中断され、スコアは無効**になります（「中断しました」と表示されます）。
- 画面を回転しても、ゲームはそのまま続きます。
- ハイスコアを消したいときは、端末の「設定 → アプリ → TapGame → ストレージ → データを削除」を行ってください。

## 6. 開発者向け：ビルドと実行
### 必要なもの
- Android Studio（同梱 JDK を使用）
- Android SDK 36
- 実機（USBデバッグ有効）またはエミュレータ

### Android Studio から
1. このフォルダ（`android-git-lab`）を開く。
2. 実機かエミュレータを選び、▶ Run `app` を押す。

### コマンドラインから（PowerShell）
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug          # APK をビルド
.\gradlew.bat testDebugUnitTest      # ユニットテストを実行
```
APK の出力先: `app\build\outputs\apk\debug\app-debug.apk`

### 実機へインストール
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

## 7. 設定を変えたいとき
| 変えたいこと | 場所 |
|---|---|
| 制限時間 | `GameViewModel.GAME_DURATION_MS` |
| コンボが続く時間 | `GameViewModel.COMBO_WINDOW_MS` |
| 称号の境界スコア | `GameUiState.kt` の `Rank` |
| 文言 | `app/src/main/res/values/strings.xml` |
| 色 | `app/src/main/res/values/colors.xml` |

詳しい仕組みは [DESIGN.md](DESIGN.md) を参照してください。
