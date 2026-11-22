package com.mc1510ty.TekitouAIStudio;

import com.mc1510ty.TekitouAIStudio.transformer.Transformer;

import java.io.IOException;
import java.util.Scanner;

public class Main {
    private Scanner scanner;
    private getValueSystemIn getvaluesystemin;
    private Transformer transformer;

    public void main(String[] args) throws IOException {

        scanner = new Scanner(System.in, System.getProperty("stdin.encoding"));
        getvaluesystemin = new getValueSystemIn(scanner);
        printStartMessage();
        IO.println("モードを選択:");
        IO.println("1: 完成したモデルを読み込ん");
        IO.println("2: チェックポイントを読み込む");
        IO.println("3: モデルを新規作成");
        int mode = getvaluesystemin.getIntRangeScanner("モード: ", 1, 3);
        if (mode == 1) {
            IO.println("まだ未対応()");
        } else if (mode == 2) {
            IO.println("まだ未対応()");
        } else if (mode == 3) {
            newModel();
        }


    }

    private void printStartMessage() {
        IO.println("======================");
        IO.println(" TekitouAIStudio");
        IO.println(" (C) 2023-2026 1510ty");
        IO.println("======================");
        IO.println();
        IO.println();
    }



    private void newModel() {
        IO.println();
        IO.println("モデル新規作成ウィザード");
        IO.println();
        IO.println("モデルタイプを選択");
        IO.println("1: 言語(会話)");
        IO.println("2: 画像");
        IO.println("3: 音声");
        IO.println("4: 動画");
        IO.println();
        int modelType = getvaluesystemin.getIntRangeScanner("モデルタイプ: ", 1, 4);
        if (modelType == 1) {
            newLanguageModel();
        } else {
            IO.println("まだ未対応()");
        }
    }

    private void newLanguageModel() {
        IO.println();
        IO.println("モデルアーキテクチャを選択");
        IO.println("1: Transformer");
        IO.println();
        int modelArchitecture = getvaluesystemin.getIntRangeScanner("モデルアーキテクチャ: ", 1,1);
        if (modelArchitecture == 1) {
            if (transformer == null) {
                transformer = new Transformer(getvaluesystemin);
            }
            transformer.newTransformerModel();
        }
    }



}
