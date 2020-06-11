package com.example.imageencryption2;

import android.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class CKGenerator {

    private static byte[] entropyPool=new byte[256];

    /***
     * STEP 5
     * An initialization vector (IV) contain 256 values
     * ranging from 0-255 is produced using CKG
     * function.
     * */
    public static byte[] sineMap(int numberofbits) {

        double initial = ThreadLocalRandom.current().nextDouble(0, 1);
        double control = ThreadLocalRandom.current().nextDouble(0, 1);

        // Generate as many bits as the number of bits the user gives
        for (int i = 0; i < numberofbits; i++) {

            byte next = (byte) (control * (Math.sin((Math.PI * initial))));

            next = threshold(next);

            entropyPool[i]=next;

        }

        Log.i("entropyPoolLog", Arrays.toString(entropyPool));

        return entropyPool;
    }

    private static byte threshold(double x) {
        if (x < 0.5) {
            return 0;
        } else {
            return 1;
        }
    }

    /***
     *
     * SHA1'i diziye uygulayamadım !
     *
     */

}
