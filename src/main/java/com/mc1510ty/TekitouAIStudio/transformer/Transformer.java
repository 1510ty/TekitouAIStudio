package com.mc1510ty.TekitouAIStudio.transformer;

import com.mc1510ty.TekitouAIStudio.getValueSystemIn;

import java.util.Objects;

public class Transformer {
    private getValueSystemIn getvaluesystemin;

    public Transformer(getValueSystemIn getvaluesystemin) {
        this.getvaluesystemin = getvaluesystemin;
    }

    public void newTransformerModel() {
        ModelConfig modelConfigTmp = new ModelConfig();

        IO.println();
        IO.println();
        IO.println("新しいTransformerモデルの作成ウィザード");
        IO.println();
        IO.println("モデル名を入力してください");
        modelConfigTmp.modelName = getvaluesystemin.taiwa("モデル名: ");
        IO.println();
        IO.println("トークナイザーを選択してください");
        IO.println("1: Byte Level BPE");
        modelConfigTmp.tokenizer = getvaluesystemin.getIntRangeScanner("トークナイザー: ",1,1);
        IO.println();
        IO.println("モデルの基本次元数を入力してください");
        IO.println("「基本次元数とは...?」という人は「?」と入力してください。大雑把な説明とおすすめの数値が出ます。");
        while (true) {
            String kihonnZigenn = getvaluesystemin.taiwa("基本次元数(説明表示は[?]): ");
            if (Objects.equals(kihonnZigenn, "")) {
                continue;
            }
            if (Objects.equals(kihonnZigenn, "?") || Objects.equals(kihonnZigenn, "？")) {
                IO.println();
                IO.println("基本次元数はモデルが何かを理解する時に使う解像度(抽象化の明確さ)です。");
                IO.println("多いほどより複雑なニュアンスを理解できるようになる可能性が高いですが、");
                IO.println("CPU、メモリの消費量が両方増えます。");
                IO.println("(かなり大雑把な説明なので参考程度に。)");
                IO.println("とりあえずのおすすめは");
                IO.println("PCのメモリが");
                IO.println("8GB: 128次元");
                IO.println("16GB: 256次元");
                IO.println("32GB: 512次元");
                IO.println("それ以降: 知りません(?)");
                IO.println();
                continue;
            }
            try {
                modelConfigTmp.hiddenSize = Long.parseLong(kihonnZigenn);
                break;
            } catch (Throwable _) {
            }
        }

        String a = getvaluesystemin.taiwa("入力");

        IO.println(a);

        IO.println(
                a.codePoints()
                        .mapToObj(cp -> String.format("U+%04X", cp))
                        .toList()
        );


        IO.println("stdin: " + System.getProperty("stdin.encoding"));
        IO.println("stdout: " + System.getProperty("stdout.encoding"));
        IO.println("native: " + System.getProperty("native.encoding"));


        while (true) {
            a = getvaluesystemin.taiwa("入力");
            IO.println(a);
        }





    }
}
