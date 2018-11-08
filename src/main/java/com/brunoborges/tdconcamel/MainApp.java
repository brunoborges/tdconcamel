package com.brunoborges.tdconcamel;

import org.apache.camel.main.Main;

public final class MainApp {

    public static void main(String[] args) throws Exception {
        System.out.println("\n\n\n\n");
        System.out.println("===============================================");
        System.out.println("Open your web browser on http://localhost:8080");
        System.out.println("Press ctrl+c to stop this example");
        System.out.println("===============================================");
        System.out.println("\n\n\n\n");

        Main main = new Main();
        main.addRouteBuilder(new TISAppCamelRoute());
        main.run();
    }

}
