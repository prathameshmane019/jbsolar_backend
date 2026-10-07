package com.prathamesh.jbsolar.service;

import java.util.regex.Pattern;

import com.prathamesh.jbsolar.api.ApiException;
import org.springframework.http.HttpStatus;

public final class MobileNumber {
    private static final Pattern ACCEPTED = Pattern.compile("^\\+?[0-9]{8,15}$");

    private MobileNumber() {
    }

    public static String normalize(String input) {
        String normalized = input.replaceAll("[\\s-]", "");
        if (!ACCEPTED.matcher(normalized).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_mobile",
                    "Mobile number must contain 8 to 15 digits and may begin with +");
        }
        String digits = normalized.startsWith("+") ? normalized.substring(1) : normalized;
        if (normalized.startsWith("+")) {
            return "+" + digits;
        }
        if (digits.length() == 10) {
            return "+91" + digits;
        }
        return "+" + digits;
    }
}
