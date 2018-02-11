// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import com.eclipsesource.json.JsonObject;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;

/**
 * Utilities - generally reusable across classes.
 */
class Util {
    // Compares two string arrays (two dimensional).
    public static boolean arraysAreDifferent(String ar1[][], String ar2[][]) {
        if (ar1 == null) {
            if (ar2 != null) {
                return true;
            }
        } else if (ar2 == null) {
            return true;
        }

        if (ar1.length != ar2.length) {
            return true;
        }

        for (int i = 0; i < ar1.length; i++) {
            if (ar1[i].length != ar2[i].length) {
                return true;
            }

            for (int j = 0; j < ar1[i].length; j++) {
                String s1 = ar1[i][j];
                String s2 = ar2[i][j];

                if (s1 == null) {
                    if (s2 != null) {
                        return true;
                    }
                } else if (s2 == null) {
                    return true;
                } else {
                    if (!s1.equals(s2)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }


    // Turns a 1.0.7+ error message to a an old JSOn style message
    // info - new style error message
    public static JsonObject getJsonErrorMessage(String info)
            throws IOException {
        JsonObject jInfo = new JsonObject();

        // Error message here comes from ZCash 1.0.7+ and is like:
        //hush-cli getinfo
        //error code: -28
        //error message:
        //Loading block index...
        LineNumberReader lnr = new LineNumberReader(new StringReader(info));
        int errCode = Integer.parseInt(lnr.readLine().substring(11).trim());
        jInfo.set("code", errCode);
        lnr.readLine();
        jInfo.set("message", lnr.readLine().trim());

        return jInfo;
    }

}
