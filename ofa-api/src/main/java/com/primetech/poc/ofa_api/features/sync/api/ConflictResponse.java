package com.primetech.poc.ofa_api.features.sync.api;

import java.util.List;

public record ConflictResponse(
    String docId,
    String winningRev,
    List<String> conflictingRevs
) {
}
