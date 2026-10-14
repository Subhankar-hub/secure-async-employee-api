package com.example.employeeapi.employee;

public final class PiiMasker {

    private PiiMasker() {
    }

    public static String maskPan(String pan) {
        if (pan == null || pan.length() < 6) {
            return "****";
        }
        return pan.substring(0, 5) + "****" + pan.charAt(pan.length() - 1);
    }
}
