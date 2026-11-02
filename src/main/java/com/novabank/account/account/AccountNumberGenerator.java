package com.novabank.account.account;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 12 digit account numbers: 4 digit NovaBank branch prefix, 7 random digits and a
 * Luhn check digit, the same scheme card numbers use to catch single digit typos.
 */
@Component
public class AccountNumberGenerator {

    private static final String BRANCH_PREFIX = "1002";
    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder sb = new StringBuilder(BRANCH_PREFIX);
        for (int i = 0; i < 7; i++) {
            sb.append(random.nextInt(10));
        }
        sb.append(luhnCheckDigit(sb.toString()));
        return sb.toString();
    }

    static int luhnCheckDigit(String digits) {
        int sum = 0;
        boolean dbl = true;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            dbl = !dbl;
        }
        return (10 - (sum % 10)) % 10;
    }
}
