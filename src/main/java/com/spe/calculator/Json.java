package com.spe.calculator;

/** Tiny helpers to build the few JSON bodies we return (avoids needing a JSON library). */
final class Json {

    private Json() {
    }

    static String error(int status, String reason, String message) {
        return "{\"status\":" + status
                + ",\"error\":\"" + escape(reason)
                + "\",\"message\":\"" + escape(message) + "\"}";
    }

    static String result(String operation, String a, String b, String result) {
        return "{\"operation\":\"" + escape(operation)
                + "\",\"a\":" + a
                + ",\"b\":" + b
                + ",\"result\":" + result + "}";
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
            }
        }
        return sb.toString();
    }
}
