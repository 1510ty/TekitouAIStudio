# Tekitou AI Studio - 独自Language Model 仕様書

## 1. 基本仕様

| 項目 | 仕様 |
|---|---|
| モデル | 独自Language Model |
| 実装言語 | Java |
| 数値精度 | double (64bit) |
| 初期アーキテクチャ | Recurrent + Dense |
| Recurrent方式 | 標準GRU |
| Dense構成 | SwiGLU FFN・Output Projection |
| Attention | 使用しない |
| 基本学習方式 | 次Token予測 |
| 損失関数 | Categorical Cross Entropy |
| Optimizer | AdamW |
| Normalization | RMSNorm |
| Residual | Pre-Norm方式 |

## 2. モデル構造

初期モデルの構造：

1. Token Embedding
2. GRU Block × GRU Layer数
3. SwiGLU FFN
4. Final RMSNorm
5. Output Projection
6. Logits出力

### Token位置ごとの処理

上記のモデル構造は、系列内の各Token位置に対して適用する。

GRU BlockはToken位置を順番に処理し、GRU Stateを次のToken位置へ引き継ぐ。

SwiGLU FFN、Final RMSNorm、Output Projectionも各Token位置で実行する。

各Token位置について、それぞれのLogitsを出力する。

系列全体に対してSwiGLU FFNやOutput Projectionを1回だけ実行する方式ではない。


学習時はSoftmaxを明示的に実行せず、Logitsから直接Cross Entropyを計算する。

推論時はTop-kおよびTop-pによるToken選択を行う。
確率分布の計算にはSoftmaxを使用する。

### 次元数

| 項目 | 初期設定 |
|---|---:|
| Model Dimension | 256 |
| Embedding Dimension | 256 |
| GRU Layer数 | 2 |
| GRU Hidden Size | 256 |
| FFN Hidden Size | 684 |
| Output Dimension | Vocabulary Size |

FFN Hidden SizeはModel Dimensionの約2.67倍とする。

Embedding DimensionとGRU Hidden SizeはModel Dimensionと同じ値にする。

v1.0では各GRU Layerの入力・出力次元を256に統一する。

## 3. Recurrent / GRU

標準GRUを採用する。

初期段階では独自改造を行わない。

### GRUの計算式

更新ゲート：

z_t = sigmoid(W_z x_t + U_z h_(t-1) + b_z)

リセットゲート：

r_t = sigmoid(W_r x_t + U_r h_(t-1) + b_r)

新しいStateの候補：

h_candidate = tanh(W_h x_t + U_h(r_t ⊙ h_(t-1)) + b_h)

State更新：

h_t = (1 - z_t) ⊙ h_(t-1) + z_t ⊙ h_candidate

### GRU内部の活性化関数

| 用途 | 関数 |
|---|---|
| Update Gate | Sigmoid |
| Reset Gate | Sigmoid |
| Candidate State | tanh |

## 4. Embedding

| 項目 | 仕様 |
|---|---|
| 次元数 | 256 |
| 初期化方式 | 正規分布 |
| 平均 | 0 |
| 標準偏差 | 0.0625 |
| 数値精度 | double |
| 学習 | 学習可能 |

初期化式：

E(i,j) ~ N(0, 0.0625²)

### Weight Tying

ConfigによってON/OFFを切り替え可能にする。

初期設定はONとする。

・ON：Input EmbeddingとOutput Projectionで重みを共有。
・OFF：それぞれ独立した重みを使用。

両方式を比較できるようにする。

## 5. FFN

初期方式はSwiGLU。

入力次元・出力次元は256、中間次元は684とする。

### SwiGLU

FFN(x) = Wo(SiLU(Wg x) ⊙ (Wu x))

Wg：ゲート側の重み
Wu：情報側の重み
Wo：出力側の重み
⊙：要素ごとの乗算

各Weightの次元：

Wg：684 × 256
Wu：684 × 256
Wo：256 × 684

SiLUの計算式：

SiLU(x) = x × sigmoid(x)

Biasは使用しない。

初期実装ではSwiGLUを使用する。
将来的にGEGLUとの比較実験を実施する。
## 6. Normalization

RMSNormを採用する。

### 設定

| 項目 | 設定 |
|---|---|
| Normalization | RMSNorm |
| epsilon | 1.0e-6 |
| Scale | 学習可能 |
| Scale初期値 | 1.0 |
| Bias | なし |

### 計算式

RMSNorm(x) = Scale × x / sqrt(mean(x²) + epsilon)

mean(x²)は、各Tokenの特徴次元方向について計算する。

系列長方向やBatch方向に対して平均を計算しない。

### 配置

・各GRU Layerの入力前
・SwiGLU FFNの入力前
・Output Projectionの直前

Pre-Norm方式とする。

各GRU Layerに独立したRMSNormを用意する。

## 7. Residual Connection

Pre-Norm方式のResidual Connectionを採用する。

GRU Layer 1、GRU Layer 2、SwiGLU FFNにResidualを適用する。

### GRU Layer 1

u1 = RMSNorm1(x0)

h1 = GRU1(u1, previousState1)

x1 = x0 + h1

### GRU Layer 2

u2 = RMSNorm2(x1)

h2 = GRU2(u2, previousState2)

x2 = x1 + h2

### SwiGLU FFN

u3 = RMSNorm3(x2)

x3 = x2 + SwiGLU(u3)

### Output

v = RMSNormFinal(x3)

logits = W_out × v + b_out

W_out：Output Projection Weight
b_out：Output Projection Bias

Output Projection Biasは学習可能とする。

### GRU State管理

各GRU Layerは独立したStateを持つ。

GRU内部のStateとResidual適用後の出力は別々に管理する。

次Tokenの処理では、GRU内部のStateを引き継ぐ。

Residual適用後の出力をGRU Stateとして使用しない。

GRU Layer数を変更した場合も、各Layerで同様の処理を適用する。

## 7.1. パラメータ初期化

各パラメータの初期化方式を以下に統一する。

| パラメータ | 初期化方式 |
|---|---|
| Embedding | Normal Distribution |
| GRU Input Weight | Xavier Uniform |
| GRU Recurrent Weight | Orthogonal |
| GRU Bias | Zero |
| SwiGLU Wg | Xavier Uniform |
| SwiGLU Wu | Xavier Uniform |
| SwiGLU Wo | Xavier Uniform |
| Output Projection Weight | Xavier Uniform |
| Output Projection Bias | Zero |
| RMSNorm Scale | 1.0 |

### GRU

各GateのInput Weightを個別にXavier Uniformで初期化する。

各GateのRecurrent Weightを個別にOrthogonalで初期化する。

Biasはすべて0で初期化する。

### Output Projection

Weight TyingがONの場合、Output Projection WeightはEmbeddingのWeightを共有する。

Weight TyingがOFFの場合、Output Projection WeightをXavier Uniformで初期化する。

Output Projection BiasはWeight Tyingの設定に関係なく独立して保持する。

Output Projection Biasは0で初期化する。

### Weight Tying

Weight TyingがONの場合、Output ProjectionはEmbeddingのWeightを共有する。

共有Weightを重複して初期化しない。

### Dropout

v1.0ではDropoutを使用しない。

## 8. Optimizer

AdamWを採用する。

### 初期設定

| パラメータ | 値 |
|---|---:|
| β1 | 0.9 |
| β2 | 0.999 |
| Epsilon | 1.0e-8 |
| Weight Decay | 0.01 |
| AMSGrad | OFF |
| Bias Correction | ON |
| Global L2 Norm Clipping | 1.0 |
| Update Target Tokens（更新閾値） | 8192 |

### Gradient蓄積

各Workerは担当する学習系列について、Full BPTTによるBackwardを実行する。

計算したGradientは平均化せず、WorkerごとのローカルなGradient蓄積領域に加算する。

同時に、その系列でLoss計算対象となった有効Target Token数をWorkerごとのローカルカウンタに加算する。

系列終了後もGradientとToken数は保持し、次の系列の計算結果を加算する。

各Workerは独立したGradient蓄積領域とTokenカウンタを持つ。

### Optimizer Updateの実行タイミング

全Workerのローカルカウンタを合算した「未更新の有効Target Token数」が **8192以上** に達した時点で、以下の更新処理を実行する。

また、Epoch終了時やフェーズ切り替え時に未更新のGradientが残っている場合（8192未満の端数）も、同様に更新処理を実行する。

1. 各Workerが保持しているローカルGradient蓄積領域およびローカル未更新カウンタを、更新処理用の確定データとして切り離して取り出し、各Worker側には新しい空（0初期化済み）の蓄積領域とカウンタを割り当てる（スワップ処理）。
2. 取り出した全Worker分のGradientを合計する。
3. 取り出した全Worker分の未更新有効Target Token数を合計する。
4. 合計した有効Target Token数でGradientを平均化する。
5. Global L2 Norm Clippingを実行する。
6. AdamWによってModel Weightを更新する。

### Global L2 Norm Clipping

全パラメータのGradientを対象にL2 Normを計算する。

Gradient全体のL2 Normが1.0を超えた場合、すべてのGradientを同じ割合で縮小する。

Gradientの方向を維持しながら、大きさを制限する。

Gradientの平均化後、AdamWによるWeight更新前に実行する。


### Weight Decay

以下のパラメータをWeight Decayの対象とする。

・Embedding Weight
・GRU Weight
・SwiGLU Weight
・Output Projection Weight

以下は対象外とする。

・すべてのBias
・RMSNorm Scale

Weight TyingがONの場合、共有Weightは1つのパラメータとして扱う。

BiasおよびRMSNormのScaleにはWeight Decayを適用しない。

Learning Rateは別途設定する。

## 9. 損失関数

Categorical Cross Entropyを採用する。

次Tokenの正解確率を高くするように学習する。

### 計算方式

学習時はSoftmaxを明示的に実行せず、Logitsから直接Cross Entropyを計算する。

Log-Sum-Expを使用し、数値的な安定性を確保する。

各TokenのCross Entropyを計算する。

終了Tokenを含む特殊TokenのLoss対象範囲は、データセット種別ごとのLoss計算マスクに従う。

### Lossの集計方式

各学習系列について、有効な正解TokenのCross Entropyを合計する。

Backwardには系列全体のLoss合計を使用する。

各Workerは担当した系列のLoss合計と有効Target Token数を、Epoch評価用の蓄積領域に加算する。
（Optimizer更新用の未更新カウンタとは独立して保持する）

Epoch終了時に、全WorkerのEpoch評価用Loss合計を統合する。

Epoch全体のLoss計算対象となる有効Target Token数で除算し、Epochの平均Lossを算出する。

Workerごとの平均Lossを単純に平均する方式は使用しない。

Padding部分はLossの計算対象に含めない。

### Loss計算マスク

各系列のTarget Tokenについて、Loss計算対象かどうかを管理する。

事前学習では、すべての有効なTarget TokenをLoss計算対象とする。

対話学習では、Assistantが生成するTokenをLoss計算対象とする。

これには以下を含む。

- Assistantのメッセージ本文
- Assistantが生成するMessage Start / Message End
- Assistantが生成するLine Break
- Draft操作Token
- Search要求Token
- ConsoleコマンドToken
- Assistant Turn End

以下はLoss計算対象外とする。

- Userから提供されるToken
- Hostが挿入するTurn境界Token
- Hostが挿入するSearch結果
- Hostが挿入するConsole結果
- Hostが挿入するMessage Start / Message End

同じToken IDであっても、モデルが生成したものかHostが挿入したものかによってLoss Maskを区別する。

Loss計算対象外のTokenもモデルへの入力コンテキストとして使用する。

後続のLoss計算対象Tokenに影響する場合、その入力を通じたGradientはBackwardの対象となる。

## 10. BPTT

基本方式はFull BPTTとする。

### 処理方針

・1つの学習系列を分割せずに処理する。
・系列の最初から最後までForwardを実行する。
・系列全体のLossを計算する。
・系列全体を対象にBackwardを実行する。
・計算したGradientをWorkerの蓄積領域に加算する。

### GRU State

新しい学習系列の開始時は、すべてのGRU Stateを0で初期化する。

系列内ではTokenごとにGRU Stateを更新する。

学習系列の終了時にはStateをリセットする。

### TurnとGRU State

GRU Stateは1つのJSONLデータ全体を通して引き継ぐ。

以下のTokenが出現してもGRU Stateはリセットしない。

・Turn User Start / End
・Turn Assistant Start / End
・Turn Search Start / End
・Turn Console Start / End
・Message Start / End

新しいJSONLデータの開始時に、すべてのGRU Stateを0で初期化する。

JSONLデータの処理終了時にGRU Stateをリセットする。

### Truncated BPTT

v1.0ではTruncated BPTTを使用しない。

## 11. バッチ処理・並列化

| 項目 | 設定 |
|---|---:|
| Batch Size（最大同時処理系列数） | 12 |
| Worker Threads | 12 |
| 数値精度 | double |

Batch Sizeは同時に処理する学習系列の最大数とする。

1 Workerにつき1つの学習系列を担当する。

### 並列処理方針

・最大12 Workerで並列処理する。
・各Workerは独立したGRU Stateを持つ。
・各Workerは独立したGradient蓄積領域を持つ。
・各Workerは担当系列のForwardとBackwardを実行する。
・系列終了後は次の未処理系列を担当する。
・全Workerの処理が終了するまでEpochを継続する。

### 学習系列の割り当て

Epoch開始時に学習系列をToken数の降順で並べる。

同じToken数の系列については順番をランダム化する。

Workerが空いた場合、次の未処理系列のうち最も長い系列を割り当てる。

系列が終了したWorkerはGRU Stateを0にリセットし、次の未処理系列を直ちに担当する。

同じEpoch内で、各学習系列は1回ずつ処理する。

各Input Tokenから次のTokenを予測する。

Token列の長さが2未満（N < 2）の系列は、有効なInput/Targetペアが形成できないため学習対象から除外する。

Turn Assistant EndはToken ID 256を使用し、自動EOS追加は行わない。

### Gradient蓄積と非同期更新

各Workerは担当した学習系列についてFull BPTTを実行し、結果を自身のローカル蓄積領域に加算する。

・ローカルGradient蓄積領域にGradientを加算する。
・ローカル未更新Tokenカウンタに有効Target Token数を加算する。
・Epoch評価用のLoss合計・Token数カウンタにも加算する。

系列の切り替え時にはGRU Stateを0に初期化する。

### Gradient蓄積と同期Optimizer Update

各Workerは担当した学習系列についてFull BPTTを実行し、
結果を自身のローカル蓄積領域に加算する。

全Workerの未更新有効Target Token数の合計が8192以上に達した場合、
Optimizer Updateの実行を要求する。

Optimizer Updateが要求された場合、以下の処理を行う。

1. 新しい学習系列の割り当てを停止する。
2. すでに処理中の系列は、現在のModel Weightを使用して最後まで処理する。
3. 全WorkerのForward・Backwardが完了するまで待機する。
4. 全WorkerのGradient蓄積領域と未更新Tokenカウンタを統合する。
5. 有効Target Token数でGradientを平均化する。
6. Global L2 Norm Clippingを実行する。
7. AdamWによってModel Weightを更新する。
8. 全WorkerのGradient蓄積領域とTokenカウンタを0に初期化する。
9. 新しいModel Weightを使用して学習を再開する。

Optimizer Updateの実行中は、すべてのWorkerの
Forward・Backwardを停止する。

更新処理が完了した後、Workerは最新のModel Weightを使用する。

8192 Tokenを超過した場合でも、処理中の系列を途中で分割せず、
その系列の完了後にOptimizer Updateを実行する。

そのため、1回のOptimizer Updateで使用される
有効Target Token数は8192を超える場合がある。

### Epoch終了時の処理

全Workerが担当する学習系列を処理し終えた時点でEpoch完了とする。

Epoch終了時に以下を実行する。

1. 未更新のGradientおよび有効Target Token数が残っている場合、残余分についてOptimizer Update（Gradient統合・平均化・Clipping・AdamW更新）を実行する。
2. 全WorkerのEpoch評価用Loss合計および有効Target Token数を統合する。
3. Epoch全体の平均Lossを計算・記録する。

### Padding

v1.0ではPaddingを使用しない。

各Workerは実際の系列長に応じて処理する。

学習系列数が12未満の場合は、存在する系列だけで処理する。

### 時間方向の処理

各学習系列の時間方向のGRU処理は逐次実行する。

## 12. 開発目標

| 項目 | 目標 |
|---|---|
| 実行環境 | CPU中心 |
| コンテキスト用メモリ | 約2GB |
| 文章生成 | 反復型生成 |

### メモリ要件

学習時：

・最低メモリ容量：16GB
・推奨メモリ容量：32GB以上

推論・会話時：

・メモリ容量8GBのPCでも実行可能にすることを目標とする。

学習時と推論時では、使用するメモリ容量の要件を分ける。

学習時はGradient、Optimizer State、Forward・Backwardの中間データなどを保持するため、より大きなメモリ容量を必要とする。

### Draft Generation

モデル自身が文章生成とDraftの管理を行う。

Draftは以下の特殊Tokenによって管理する。

- Draft Start：新しいDraftの作成・上書き開始
- Draft End：Draftの作成終了
- Draft Push：現在のDraftを正式な回答として公開

### Draftの管理

DraftはHost側で以下の2つの状態を管理する。

- Current Draft：最後にDraft Endまで完了したDraft
- Editing Draft：現在編集中のDraft

初期状態ではCurrent Draftは存在しない。

Draft Startが出力された場合、現在のEditing Draftを破棄し、新しいEditing Draftを空の状態から開始する。

既存のCurrent Draftは維持する。

Draft Endが出力された場合、Editing Draftの内容をCurrent Draftに反映する。

Draft Pushが出力された場合、Current Draftを正式な回答として公開する。

Draft Push後もCurrent Draftの内容は維持する。

次のDraft Start～Draft Endによって上書きされるまでは、同じ内容を保持する。

### Draft Pushの動作

Draft PushがDraft Endより前に出力された場合、編集中のEditing Draftは公開しない。

Current Draftが存在する場合は、その内容を公開する。

Current Draftが存在しない場合は、何も公開しない。

一度Draft Pushによって公開された文章は、その後Current Draftが上書きされても変更しない。

Draft Pushは複数回実行できる。

各Pushで公開された文章は、実行された順番で表示する。

Assistant Turn終了時に未完成のEditing Draftが残っている場合は破棄する。

Current Draftは維持する。

### ThinkingとDraftの表示

通常のAssistant MessageはThinkingとして扱う。

Thinkingはコンソール・GUI側で表示方法を制御できるようにする。

Thinkingは灰色文字などで表示し、クリックによって折りたたみ・展開できるようにする。

DraftはDraft Pushされるまで正式な回答として表示しない。

Draft Pushされた文章は通常の回答として表示する。

推論途中のThinking、Draft、公開済みの回答は、それぞれ独立して管理する。

最後に文章を再生成して清書する処理は行わない。

## 13. 将来の拡張候補

- MoE（Mixture of Experts）
- Neural Memory
- Archive Memory
- Semantic Memory
- Structural Memory
- 補助学習（記憶再現・マスク復元など）
- GRUの独自改良
- GEGLUとの比較実験

これらは初期モデルには含めず、将来の拡張候補とする。

## 14. 学習回数と学習率

目標の誤差に到達するまで学習する。

固定Epoch数ではなく、目標性能を基準に終了する方式。
### Learning Rate

| Epoch | Learning Rate |
|---|---:|
| 1 | 0.0001 |
| 2 | 0.0005 |
| 3 | 0.001 |
| 4以降 | 0.001 |

### Warmup

最初の3 EpochでLearning Rateを増加させる。

・1 Epoch目：0.0001
・2 Epoch目：0.0005
・3 Epoch目：0.001
・4 Epoch目以降：0.001固定

Warmup終了後のLearning Rate Decayは導入しない。

Learning RateはEpoch単位で管理する。

## 15. 学習データセット

学習は以下の3フェーズで実施する。

### Phase 1：事前学習

一般文章やプログラムコードなどを使用する。

### Phase 2：対話学習

対話形式のデータを使用する。

### Phase 3：ミックス学習

事前学習データと対話学習データを混合して使用する。

ミックス学習では、事前学習データと対話学習データの両方を使用する。

各Epochでは、事前学習データと対話学習データに含まれるすべての学習系列を、それぞれ1回ずつ処理する。

データセット間のToken数比率は固定しない。

### フェーズの切り替え

フェーズは以下の順番で実行する。

1. 事前学習
2. 対話学習
3. ミックス学習

フェーズ切り替え時には以下の処理を行う。

・Model Weightを引き継ぐ。
・AdamWのOptimizer Stateを引き継ぐ。
・Learning Rateを引き継ぐ。
・GRU Stateをリセットする。
・学習データセットを切り替える。

### データの順序

Epoch開始時に学習系列をToken数の降順で並べる。

同じToken数の系列については順番をランダム化する。

系列内のToken順序は変更しない。

異なる文書を1つの系列として連結しない。

各Epochですべての学習系列を1回ずつ処理する。

### 対話データ形式

対話学習データはJSONL形式とする。

1行につき1つの会話データを保存する。

各行はJSON Objectとし、以下の構造を持つ。

```json
{
  "turns": [
    {
      "role": "user",
      "events": [
        {
          "type": "message",
          "content": [
            {"type": "text", "value": "こんにちは"}
          ]
        }
      ]
    },
    {
    "role": "assistant",
    "events": [
        {
        "type": "message",
        "content": [
            {"type": "draft_start"},
            {"type": "text", "value": "こんにちは！"},
            {"type": "draft_end"},
            {"type": "draft_push"}
        ]
        }
    ]
    }
  ]
}
```

各Turnは以下の要素で構成する。

- role：発言者の種類
- events：Turn内のイベントを時系列順に格納する配列

使用するRoleは以下のとおり。

- user：Userの発言
- assistant：Assistantの発言

Search結果とConsole結果はAssistant Turn内のEventとして表現する。

### Event形式

各Eventは以下の要素で構成する。

- type：Eventの種類
- content：Eventの内容

使用するEvent Typeは以下のとおり。

| Type | 用途 |
|---|---|
| message | 通常のMessage |
| search_result | Hostから返される検索結果 |
| console_result | Hostから返されるConsole結果 |

### Content形式

Contentは複数の要素を格納できる配列とする。

各要素はtypeによって種類を区別する。

| Content Type | 用途 |
|---|---|
| text | 通常の文章 |
| line_break | 改行 |
| draft_start | Draft開始 |
| draft_end | Draft終了 |
| draft_push | Draft公開 |
| search_start | Search要求開始 |
| search_end | Search要求終了 |
| console_start | Console要求開始 |
| console_end | Console要求終了 |

text要素はvalueに文章を格納する。

line_breakや各種特殊Token要素は、独立した構造要素として表現する。

実際の改行はline_break要素で表現し、文章中の文字列と区別する。

通常の文章中に特殊Tokenと同じ文字列が含まれていても、text要素内であれば特殊Tokenとして扱わない。

### Turn・Messageの構造

1つのJSONLデータを1つの学習系列とする。

Turnはturns配列の順序で管理する。

Assistant Turnには複数のEventを含めることができる。

Message Eventは複数のContent要素を含めることができる。

Search結果とConsole結果は、Assistant Turnの途中にEventとして挿入する。

### Search・Console要求

Assistantが検索を要求する場合、Message EventのContent内に以下の要素を順番に配置する。

1. search_start
2. 検索内容を表すText
3. search_end

Consoleコマンドも同様に、console_start、コマンド内容、console_endの順に配置する。

Search結果はsearch_result Event、Console結果はconsole_result Eventとして保存する。

### Draft操作

Draft Start、Draft End、Draft PushをContent要素として保存する。

DraftはAssistant Turnの内部で複数回作成・上書き・公開できる。

### Loss計算対象

対話学習では、UserおよびConsoleから提供されるTokenをコンテキストとして使用する。

Assistantが生成するTokenをLoss計算対象とする。

Loss計算対象外のTokenを通じても、後続のLoss計算対象Tokenに影響するGradientはBackwardの対象とする。

## 16. 学習評価・終了条件

学習性能の評価にはValidation Cross Entropyを使用する。

### Validation Data

Training Dataとは独立したValidation Dataを用意する。

Validation DataはModel Weightの学習には使用しない。

ValidationではForwardとLoss計算のみを実行する。

Backward、Gradient蓄積、Optimizer Updateは実行しない。

Validation Dataは学習フェーズに対応するデータを使用する。

### Validation時のLoss計算

ValidationでもTrainingと同じLoss計算マスクを使用する。

対話学習のValidationでは、Assistantが生成するTokenをLoss計算対象とする。

UserおよびHostから提供されるSearch・Consoleの結果はLoss計算対象外とする。

Search TurnおよびConsole Turnの境界Tokenと、Hostが挿入するMessage Start / Message EndもLoss計算対象外とする。

Validation LossはLoss計算対象となった有効Target Token数で平均化する。

### 評価方法

・各Epoch終了時に評価する。
・Training Dataとは別のValidation Dataを使用する。
・Validation時は8192 TokenごとのスワップやOptimizer Updateは行わず、全Validation系列のLoss合計と有効Target Token数を集計して平均化する。
・Training LossとValidation Lossを別々に記録する。

### 学習終了条件

各フェーズに個別の目標Validation Cross Entropyを設定可能にする。

初期目安は1.5とする。

Validation Lossが指定された目標値以下になった場合、そのフェーズの学習を終了する。

目標値はConfigから変更可能にする。

目標値は固定せず、学習データの内容や量、実際の学習結果に応じて手動で調整する。

## 17. Tokenizer連携

Language ModelはTokenizerからToken IDを受け取る。

### 入出力

| 項目 | 内容 |
|---|---|
| Input | Token ID |
| Output | Vocabulary Size次元のLogits |
| Vocabulary Size | Tokenizerから取得 |
| Assistant Turn End ID | 256 |

### 特殊Token ID

以下の特殊Token IDを固定する。

| Token | ID | 用途 |
|---|---:|---|
| <\|turn_assistant_end\|> | 256 | Assistant Turn終了 |
| <\|message_end\|> | 257 | Message終了 |
| <\|line_break\|> | 258 | 改行 |
| <\|turn_user_start\|> | 259 | User Turn開始 |
| <\|turn_user_end\|> | 260 | User Turn終了 |
| <\|turn_assistant_start\|> | 261 | Assistant Turn開始 |
| <\|turn_console_start\|> | 262 | Console Turn開始 |
| <\|turn_console_end\|> | 263 | Console Turn終了 |
| <\|message_start\|> | 264 | Message開始 |
| <\|draft_start\|> | 265 | Draft開始 |
| <\|draft_end\|> | 266 | Draft終了 |
| <\|draft_push\|> | 267 | Draft公開 |
| <\|search_start\|> | 268 | Search要求開始 |
| <\|search_end\|> | 269 | Search要求終了 |
| <\|console_start\|> | 270 | Consoleコマンド開始 |
| <\|console_end\|> | 271 | Consoleコマンド終了 |
| <\|turn_search_start\|> | 272 | Search Turn開始 |
| <\|turn_search_end\|> | 273 | Search Turn終了 |

### Token IDの割り当て規則

Token IDは以下の範囲で用途を分ける。

・0～255：Byte fallback用Token
・256～273：特殊Token
・274以降：通常Token

Byte fallback用Tokenは256種類とし、
各Token IDを対応するByte値に固定する。

特殊Token IDは上記の特殊Token ID表に従い、
割り当てを変更しない。

通常Tokenには274以上のToken IDを割り当てる。

各Token IDは重複してはならない。

Vocabulary Sizeは最低274とする。

### Token生成元

モデルが生成するToken：

- Message Start / Message End
- Line Break
- Draft Start / Draft End / Draft Push
- Search Start / Search End
- Console Start / Console End
- Turn Assistant End

Hostが自動挿入するToken：

- Turn User Start / Turn User End
- Turn Assistant Start
- Turn Search Start / Turn Search End
- Turn Console Start / Turn Console End

Message Start / Message Endは、AssistantのMessageではモデルが生成し、User・Search結果・Console結果のMessageではHostが挿入する。

### Tokenizer読み込み時の検証

Tokenizerの読み込み時に、以下の項目を検証する。

・Byte fallback用Tokenが0～255に正しく割り当てられていること。
・特殊Tokenが256～273に正しく割り当てられていること。
・通常Tokenに274以上のIDが割り当てられていること。
・すべてのToken IDが重複していないこと。
・すべてのToken IDがVocabulary Sizeの範囲内であること。

検証に失敗した場合はTokenizerの読み込みを失敗させる。

### JSONL変換

TokenizerはJSONLを解析し、構造に応じて特殊Tokenを挿入する。

textは通常のTokenizerでToken IDに変換する。

line_breakはToken ID 258に変換する。

Draft、Search、Console関連のContent要素は対応する特殊Token IDに変換する。

User TurnではHostがTurn User StartとTurn User Endを挿入する。

Assistant TurnではHostがTurn Assistant Startを挿入し、AssistantがTurn Assistant Endを生成する。

Search結果は以下のToken列に変換する。

```text
<|turn_search_start|>
<|message_start|>
検索結果
<|message_end|>
<|turn_search_end|>
```

Console結果は以下のToken列に変換する。

```text
<|turn_console_start|>
<|message_start|>
Console結果
<|message_end|>
<|turn_console_end|>
```

これらの外部処理結果はHostが挿入する。

### Assistant Turn Endの挿入

Assistant Turnの終了位置には、
Token ID 256（Turn Assistant End）を1回挿入する。

このTokenはJSONL変換時に、
Assistant Turnの構造に基づいて自動的に挿入する。

Assistant Turn内に複数のMessage、Search結果、
Console結果が存在する場合も、
すべてのEventを変換した後に1回だけ挿入する。

Turn Assistant Endはモデルが生成するTokenとして扱い、
対話学習ではLoss計算対象とする。

Hostが推論時にTurn Assistant Endを自動挿入することはない。

この処理は対話学習データの変換に適用し、
事前学習データには自動適用しない。

既存のEOS自動追加処理とは区別する。

### 学習系列

BOSは使用しない。

1つのJSONLデータを1つの学習系列として扱う。

JSONLから生成したToken列を以下のように1 Tokenずらす。

Token列：
[Token1, Token2, ..., TokenN]

Input：
[Token1, Token2, ..., Token(N-1)]

Target：
[Token2, Token3, ..., TokenN]

各Input Tokenから次のTokenを予測する。

Turn Assistant EndはToken ID 256を使用し、自動EOS追加は行わない。

## 18. 推論

文章生成はAutoregressive方式とする。

1. 入力メッセージをTokenizerでToken IDに変換する。
2. 新しい会話の場合のみGRU Stateを0で初期化する。
   既存の会話を継続する場合は、その会話のGRU Stateを引き継ぐ。
3. 新しく追加された入力Token（User入力の `<|turn_user_end|>` まで）を順番にモデルへ渡す。
4. Hostが `<|turn_assistant_start|>` をモデルへ渡し、GRU Stateを更新する。
5. モデルからLogitsを取得する。
6. Top-kおよびTop-pによってToken候補を選択する。
7. 選択したTokenを次の入力としてモデルへ渡す。
8. Turn Assistant Endが出力されるまで6〜7を繰り返す。

### GRU State管理

GRU Stateは会話ごとに保持する。

新しい会話の開始時は、すべてのGRU Stateを0で初期化する。

同一会話を継続する場合は、前回のGRU Stateを保持し、
新しく追加された入力Tokenだけをモデルへ渡す。

過去の会話履歴を毎回再入力してはならない。

モデルに入力されたTokenごとにGRU Stateを更新する。

モデルが生成したTokenは次の入力としてモデルへ渡し、
その処理によってGRU Stateを更新する。

Turn Assistant Endが生成された場合も、
当該Tokenを1回モデルへ渡してGRU Stateを更新してから
生成を終了する。

Turn Assistant Endが生成されてもGRU Stateはリセットしない。

会話のリセットまたは新しい会話の開始時に、
それまでのGRU Stateを破棄する。

### 初期設定

| 項目 | 設定 |
|---|---|
| Generation方式 | Autoregressive |
| BOS | 使用しない |
| 初期GRU State | 0 |
| Token選択方式 | Temperature + Top-k + Top-p |
| Temperature | 1.0 |
| 終了条件 | Turn Assistant EndまたはMax New Tokens |

Temperature、Top-k、およびTop-pのパラメータはConfigから設定可能にする。

Softmax適用前にLogitsをTemperatureで除算する（Temperature = 1.0 の場合は実質無変更）。

具体的なk・pの値と適用順序は別途決定する。

Turn Assistant End（Token ID 256）が生成された場合、生成を終了する。

Message End、Turn User End、Turn Console Endが生成・入力されても、それだけでは生成を終了しない。

入力プロンプトにTurn Assistant Endが含まれていても、それだけでは生成を終了しない。

生成処理中に新しく出力されたTokenがTurn Assistant Endである場合のみ停止する。

Consoleからの応答が返された場合は、その結果を入力コンテキストに追加し、Assistant Turnを継続する。

### 対話生成時の入力形式

Userの入力は以下の構造で表現する。

<|turn_user_start|>
<|message_start|>
ユーザーの発言
<|message_end|>
<|turn_user_end|>

モデルはTurn Assistant Startから生成を開始する。

Assistant Turn内では複数のMessageを出力できる。

Console Turnをネストすることができる。

Turn Assistant Endが生成された場合、Assistantの応答を終了する。

Userの入力は以下の構造で表現する。

<|turn_user_start|>
<|message_start|>
ユーザーの発言
<|message_end|>
<|turn_user_end|>

続いてHostが `<|turn_assistant_start|>` を入力してGRU Stateを更新したのち、モデルがAssistant Turn内のToken生成を開始する。

Assistant Turn内では複数のMessageを出力できる。

Assistant Turnの内部に複数のSearch Turn / Console Turnを順次挿入できる。

Turn Assistant Endが生成された場合、Assistantの応答を終了する。

### Logits Masking

モデルが生成してはいけない特殊Tokenや、現在の生成状態では不正となる特殊Tokenを制限するため、Logits Maskingを使用する。

Host専用TokenのLogitsは選択できないようにマスクする。

また、Message・Turn・Draft・Search・Consoleの状態に応じて、不正なTokenの生成を制限する。

Logits MaskingはToken選択処理の前に実行する。

### Search・Consoleの実行

モデルがSearch要求またはConsoleコマンドを生成した場合、
通常の生成処理と同様に、生成された各Tokenを
GRUへ1回入力してStateを更新する。

Search EndまたはConsole Endによって要求内容の終了を確認する。

その後、Message Endが生成され、
GRU Stateの更新が完了した時点でモデルの生成を一時停止する。

Hostは対応する外部処理を実行する。

Hostは処理結果を対応するSearch TurnまたはConsole Turnとして
入力コンテキストに追加する。

追加した結果Tokenは、保持しているGRU Stateに対して
順番に入力し、それぞれ1回ずつStateを更新する。

結果Tokenの入力がすべて完了したら、
最後の入力処理で得られたLogitsから
Assistant Turnの生成を再開する。

過去の会話履歴や外部処理前のTokenを再入力してはならない。

### Token入力とState更新の規則

モデルへ入力されたTokenは、入力処理によって
GRU Stateを1回だけ更新する。

モデルが生成したTokenも同様に、
生成後にモデルへ1回入力してGRU Stateを更新する。

以下のTokenも例外としない。

- Message End
- Search End
- Console End
- Turn Assistant End
- その他すべての特殊Token

生成Tokenを次の入力として渡す処理を省略してはならない。

同じTokenを重複してモデルへ入力してはならない。

Turn Assistant Endについても、
生成後に1回モデルへ入力してStateを更新する。

ただし、その後のLogitsによるToken選択は行わず、
Assistant Turnを終了する。