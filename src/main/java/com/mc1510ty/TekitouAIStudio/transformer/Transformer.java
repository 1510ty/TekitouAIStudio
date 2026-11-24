package com.mc1510ty.TekitouAIStudio.transformer;

import com.mc1510ty.TekitouAIStudio.getValueSystemIn;

import java.util.Objects;

public class Transformer {
    private getValueSystemIn getvaluesystemin;

    public Transformer(getValueSystemIn getvaluesystemin) {
        this.getvaluesystemin = getvaluesystemin;
    }

    public ModelConfig newTransformerModel() {
        ModelConfig modelConfigTmp = new ModelConfig();

        IO.println();
        IO.println();
        IO.println("新しいTransformerモデルの作成ウィザード");
        IO.println("ヒント: これから入力する任意の数値(選択などではない)には2の倍数など、割り切れる物をおすすめします。");
        while (true) {
            IO.println();
            IO.println("モデル名を入力してください");
            modelConfigTmp.modelName = getvaluesystemin.taiwa("モデル名: ");
            IO.println();
            IO.println("トークナイザーを選択してください");
            IO.println("1: Byte Level BPE");
            modelConfigTmp.tokenizer = getvaluesystemin.getIntRangeScanner("トークナイザー: ", 1, 1);
            IO.println();
            IO.println("モデルの内部次元数を入力してください");
            IO.println("「内部次元数とは...?」という人は「?」と入力してください。説明が表示されます。");
            while (true) {
                String kihonnZigenn = getvaluesystemin.taiwa("内部次元数(説明表示は[?]): ");
                if (Objects.equals(kihonnZigenn, "")) {
                    continue;
                }
                if (Objects.equals(kihonnZigenn, "?") || Objects.equals(kihonnZigenn, "？")) {
                    IO.println();
                    IO.println("モデルが情報を内部で表現するための幅を決める設定です。");
                    IO.println("大きいほど表現できる情報の幅が広がりますが、");
                    IO.println("計算量やメモリ使用量も増加します。");
                    IO.println("(by GPT-6-Luna?-Thinking)");
                    IO.println();
                    continue;
                }
                try {
                    modelConfigTmp.hiddenSize = Long.parseLong(kihonnZigenn);
                    break;
                } catch (Throwable _) {
                }
            }
            IO.println();
            IO.println("Transformer層数を入力してください");
            IO.println("「Transformer層数とは...?」という人は「?」と入力してください。説明が表示されます。");
            while (true) {
                String input = getvaluesystemin.taiwa("Transformer層数(説明表示は[?]): ");
                if (Objects.equals(input, "")) {
                    continue;
                }
                if (Objects.equals(input, "?") || Objects.equals(input, "？")) {
                    IO.println();
                    IO.println("モデルが情報を処理する層の数を決める設定です。");
                    IO.println("多いほど複雑な処理を学習できますが、");
                    IO.println("計算量やメモリ使用量も増加します。");
                    IO.println();
                    continue;
                }
                try {
                    modelConfigTmp.layerCount = Long.parseLong(input);
                    break;
                } catch (Throwable _) {
                }
            }
            IO.println();
            IO.println("AttentionHead数を入力してください");
            IO.println("「AttentionHead数とは...?」という人は「?」と入力してください。説明が表示されます。");
            while (true) {
                String input = getvaluesystemin.taiwa("AttentionHead数(説明表示は[?]): ");
                if (Objects.equals(input, "")) {
                    continue;
                }
                if (Objects.equals(input, "?") || Objects.equals(input, "？")) {
                    IO.println();
                    IO.println("Attentionを複数のHeadに分けて処理する数です。");
                    IO.println("各Headの次元数は、Hidden Size ÷ Head数で決まります。");
                    IO.println();
                    continue;
                }
                try {
                    modelConfigTmp.attentionHeads = Long.parseLong(input);
                    if (modelConfigTmp.hiddenSize % modelConfigTmp.attentionHeads != 0) {
                        IO.println("Hidden Size ÷ Headの余りが0になるようにしてください。");
                        continue;
                    }
                    break;
                } catch (Throwable _) {
                }
            }
            IO.println();
            modelConfigTmp.intermediateSize =
                    modelConfigTmp.hiddenSize * 8 / 3;
            IO.println("自動計算されたFFN内部次元数: " + modelConfigTmp.intermediateSize + "次元");
            IO.println("「FFN内部次元数とは...?」という人は「?」と入力してください。説明が表示されます。");
            IO.println("通常は自動計算値をそのまま入力すれば問題ありません。");
            while (true) {
                String input = getvaluesystemin.taiwa("FFN内部次元数(説明表示は[?]): ");
                if (Objects.equals(input, "")) {
                    continue;
                }
                if (Objects.equals(input, "?") || Objects.equals(input, "？")) {
                    IO.println();
                    IO.println("Attention後の情報処理に使うFFNの中間層の大きさを決める設定です。");
                    IO.println("大きいほどFFNの規模が大きくなりますが、");
                    IO.println("計算量やメモリ使用量も増加します。");
                    IO.println();
                    continue;
                }
                try {
                    modelConfigTmp.intermediateSize = Long.parseLong(input);
                    break;
                } catch (Throwable _) {
                }
            }
            IO.println();
            IO.println("最大コンテキスト長を入力してください");
            IO.println("「最大コンテキスト長とは...?」という人は「?」と入力してください。説明が表示されます。");
            while (true) {
                String input = getvaluesystemin.taiwa("最大コンテキスト長(説明表示は[?]): ");
                if (Objects.equals(input, "")) {
                    continue;
                }
                if (Objects.equals(input, "?") || Objects.equals(input, "？")) {
                    IO.println();
                    IO.println("モデルが一度に扱えるトークン数の上限を決める設定です。");
                    IO.println("大きいほど長い文章や会話を扱えますが、");
                    IO.println("計算量やメモリ使用量も増加します。");
                    IO.println();
                    continue;
                }
                try {
                    modelConfigTmp.maxSequenceLength = Long.parseLong(input);
                    break;
                } catch (Throwable _) {
                }
            }
            IO.println();
            IO.println("KVヘッド数を入力してください");
            IO.println("「KVヘッド数とは...?」という人は「?」と入力してください。説明が表示されます。");
            IO.println("困ったらAttentionHead数と同じ(" + modelConfigTmp.attentionHeads + ")、または2分の1(" + modelConfigTmp.attentionHeads / 2 + ")をおすすめします。");
            while (true) {
                String input = getvaluesystemin.taiwa("KVヘッド数(説明表示は[?]): ");
                if (Objects.equals(input, "")) {
                    continue;
                }
                if (Objects.equals(input, "?") || Objects.equals(input, "？")) {
                    IO.println();
                    IO.println("KeyとValueを生成するHeadの数を決める設定です。");
                    IO.println("少なくすると、複数のQuery HeadでKeyとValueを共有します。");
                    IO.println("KV Cacheのメモリ使用量を削減できますが、モデルの品質に影響する場合があります。");
                    IO.println();
                    continue;
                }
                try {
                    long kvHeads = Long.parseLong(input);

                    if (kvHeads <= 0 ||
                            kvHeads > modelConfigTmp.attentionHeads ||
                            modelConfigTmp.attentionHeads % kvHeads != 0) {

                        IO.println("KV Head数はAttention Head数の約数にしてください。");
                        continue;
                    }

                    modelConfigTmp.numKeyValueHeads = kvHeads;
                    break;

                } catch (NumberFormatException _) {
                    IO.println("整数を入力してください。");
                }
            }
            IO.println();
            IO.println();
            IO.println("モデルの種類: Transformer");
            IO.println("モデル名: " + modelConfigTmp.modelName);
            IO.println("トークナイザー: " + getTokenizerName(modelConfigTmp.tokenizer));
            IO.println("内部次元数: " + modelConfigTmp.hiddenSize);
            IO.println("Transformer層数: " + modelConfigTmp.layerCount);
            IO.println("AttentionHead数: " + modelConfigTmp.attentionHeads);
            IO.println("FFN内部次元数: " + modelConfigTmp.intermediateSize);
            IO.println("最大コンテキスト長: " + modelConfigTmp.maxSequenceLength);
            IO.println("KVヘッド数: " + modelConfigTmp.numKeyValueHeads);
            if (getvaluesystemin.getYesNo("上記の設定でよろしいですか？: ")) {
                return modelConfigTmp;
            }
        }
    }

    private String getTokenizerName(int tokenizer) {
        if (tokenizer == 1) {
            return "Byte Level BPE";
        }
        return "不明なトークナイザー名";
    }


}
