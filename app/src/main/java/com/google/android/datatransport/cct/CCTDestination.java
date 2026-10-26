package com.google.android.datatransport.cct;

import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.runtime.EncodedDestination;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Linkage stub for bundled ML Kit Latin OCR.
 *
 * {@code TextRecognition.getClient} builds a logger that reads
 * {@code CCTDestination.INSTANCE} before any pixels are recognized. The real
 * class lives in {@code transport-backend-cct}, which also contains the
 * Clearcut HTTP uploader and merges {@code INTERNET}. That artifact stays
 * excluded. This type only satisfies the constructor; {@link #getSupportedEncodings}
 * advertises the proto and json encodings ML Kit asks for, so logger setup
 * does not throw. Nothing in this class opens a socket. The HTTP backend
 * class from transport-backend-cct is not part of this app.
 */
public final class CCTDestination implements EncodedDestination {
    public static final CCTDestination INSTANCE = new CCTDestination();

    private CCTDestination() {
    }

    @Override
    public String getName() {
        return "cct";
    }

    @Override
    public byte[] getExtras() {
        return new byte[0];
    }

    @Override
    public Set<Encoding> getSupportedEncodings() {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                Encoding.of("proto"),
                Encoding.of("json")
        )));
    }
}
