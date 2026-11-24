package com.mc1510ty.TekitouAIStudio;

import java.util.Objects;
import java.util.Scanner;

public class getValueSystemIn {

    private final Scanner scanner;

    public getValueSystemIn(Scanner scanner) {
        this.scanner = scanner;
    }

    public int getIntRangeScanner(String message, int range1, int range2) {
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

    public int getIntScanner(String message) {
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

    public String taiwa(String message) {
        String str = "";
        while (Objects.equals(str, "")) {
            IO.print(message);
            str = scanner.nextLine();
        }
        return str;
    }

    public boolean getYesNo(String message) {
        while (true) {
            String str = taiwa(message);
            if (Objects.equals(str, "y")) {
                return true;
            } else if (Objects.equals(str, "n")) {
                return false;
            }
        }
    }
}
