package com.mc1510ty.TekitouAIStudio.transformer;

import com.mc1510ty.TekitouAIStudio.getValueSystemIn;

public class Transformer {
    private getValueSystemIn getvaluesystemin;

    public Transformer(getValueSystemIn getvaluesystemin) {
        this.getvaluesystemin = getvaluesystemin;
    }

    public void newTransformerModel() {
        IO.println();
        IO.println();
        IO.println("新しいTransformerモデルの作成ウィザード");
        IO.println();
        IO.println("モデル名を入力してください");
        String modelName = getvaluesystemin.taiwa("モデル名: ");
        IO.println();
        IO.println("トークナイザーを選択してください");
        IO.println("1: Byte Level BPE");
        int tokenizer = getvaluesystemin.getIntRangeScanner("トークナイザー: ",1,1);


    }
}
