package com.deluxe.sonoyuncu.utils;
public final class Banner {
    private static final String[] ART = {
        "      $$\           $$\                                 $$\ $$\   $$\ $$\   $$\ $$$$$$$$\ ",
        "      $$ |          $$ |                              $$$$ |$$ |  $$ |$$ |  $$ |\____$$  |",
        " $$$$$$$ | $$$$$$\  $$ |$$\   $$\ $$\   $$\  $$$$$$\  \_$$ |$$ |  $$ |$$ |  $$ |    $$  / ",
        "$$  __$$ |$$  __$$\ $$ |$$ |  $$ |\$$\ $$  |$$  __$$\   $$ |$$$$$$$$ |$$$$$$$$ |   $$  /  ",
        "$$ /  $$ |$$$$$$$$ |$$ |$$ |  $$ | \$$$$  / $$$$$$$$ |  $$ |\_____$$ |\_____$$ |  $$  /   ",
        "$$ |  $$ |$$   ____|$$ |$$ |  $$ | $$  $$<  $$   ____|  $$ |      $$ |      $$ | $$  /    ",
        "\$$$$$$$ |\$$$$$$$\ $$ |\$$$$$$  |$$  /\$$\ \$$$$$$$\ $$$$$$\     $$ |      $$ |$$  /     ",
        " \_______| \_______|\__| \______/ \__/  \__| \_______|\______|    \__|      \__|\__/      ",
        "                                                                       github.com/NullAway  "
    };
    private Banner() { }
    public static void print() {
        System.out.println();
        for (int i = 0; i < ART.length; i++) {
            System.out.println("  " + ConsoleColors.gradient(ART[i], i % 3));
        }
        System.out.println();
    }
}
