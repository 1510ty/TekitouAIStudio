package com.mc1510ty.TekitouAIStudio;

import com.mc1510ty.TekitouAIStudio.tokenizer.bytelevelbpe.ByteLevelBPE;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

public class Main {
    private Scanner scanner;

    public void main(String[] args) throws IOException {


        scanner = new Scanner(System.in);
        printStartMessage();
        IO.println("モードを選択:");
        IO.println("1: 完成したモデルを読み込ん");
        IO.println("2: チェックポイントを読み込む");
        IO.println("3: モデルを新規作成");
        int mode = getIntRangeScanner("モード: ", 1, 3);
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
        int modelType = getIntRangeScanner("モデルタイプ: ", 1, 4);
        if (modelType == 1) {
            newLanguageModel();
        } else {
            IO.println("まだ未対応()");
        }
    }

    private void newLanguageModel() {
        IO.println();
        IO.println("モデルアーキテクチャを選択");
        IO.println("1: 独自モデル(名称未定)");
        IO.println();
        int modelArchitecture = getIntRangeScanner("モデルアーキテクチャ: ", 1,1);
        if (modelArchitecture == 1) {
            executableDokuziModel();
        }
    }




    private int getIntRangeScanner(String message, int range1, int range2) {
        int interger;
        while (true) {
            interger = getIntScanner(message);
            if (interger >= range1 && interger <= range2) {
                break;
            }
            IO.println(range1 + "～" + range2 + "の範囲でお願いします");
        }
        return interger;
    }

    private int getIntScanner(String message) {
        int suuti;
        while (true) {
            String str = taiwa(message);
            try {
                suuti = Integer.parseInt(str);
                return suuti;
            } catch (Throwable _) {
                IO.println("intでお願いします");
            }
        }
    }

    private String taiwa(String message) {
        String str = "";
        while (Objects.equals(str, "")) {
            IO.print(message);
            str = scanner.nextLine();
        }
        return str;
    }

c
}
