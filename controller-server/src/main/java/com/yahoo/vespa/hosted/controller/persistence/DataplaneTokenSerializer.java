// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.DataplaneTokenVersions;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.FingerPrint;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.TokenId;

import java.time.Instant;
import java.util.List;

/**
 * @author mortent
 */
public class DataplaneTokenSerializer {

    private static final String dataplaneTokenField = "dataplaneToken";
    private static final String idField = "id";
    private static final String tokenVersionsField = "tokenVersions";
    private static final String fingerPrintField = "fingerPrint";
    private static final String checkAccessHashField = "checkAccessHash";
    private static final String creationTimeField = "creationTime";
    private static final String authorField = "author";

    public static Slime toSlime(List<DataplaneTokenVersions> dataplaneTokenVersions) {
        Slime slime = new Slime();
        Cursor cursor = slime.setObject();
        Cursor array = cursor.setArray(dataplaneTokenField);
        dataplaneTokenVersions.forEach(tokenMetadata -> {
            Cursor tokenCursor = array.addObject();
            tokenCursor.setString(idField, tokenMetadata.tokenId().value());
            Cursor versionArray = tokenCursor.setArray(tokenVersionsField);
            tokenMetadata.tokenVersions().forEach(version -> {
                Cursor versionCursor = versionArray.addObject();
                versionCursor.setString(fingerPrintField, version.fingerPrint().value());
                versionCursor.setString(checkAccessHashField, version.checkAccessHash());
                versionCursor.setLong(creationTimeField, version.creationTime().toEpochMilli());
                versionCursor.setString(creationTimeField, version.creationTime().toString());
                versionCursor.setString(authorField, version.author());
            });
        });
        return slime;
    }

    public static List<DataplaneTokenVersions> fromSlime(Slime slime) {
        Cursor cursor = slime.get();
        return SlimeUtils.entriesStream(cursor.field(dataplaneTokenField))
                .map(entry -> {
                    TokenId id = TokenId.of(entry.field(idField).asString());
                    List<DataplaneTokenVersions.Version> versions = SlimeUtils.entriesStream(entry.field(tokenVersionsField))
                            .map(versionCursor -> {
                                FingerPrint fingerPrint = FingerPrint.of(versionCursor.field(fingerPrintField).asString());
                                String checkAccessHash = versionCursor.field(checkAccessHashField).asString();
                                Instant creationTime = SlimeUtils.instant(versionCursor.field(creationTimeField));
                                String author = versionCursor.field(authorField).asString();
                                return new DataplaneTokenVersions.Version(fingerPrint, checkAccessHash, creationTime, author);
                            })
                            .toList();
                    return new DataplaneTokenVersions(id, versions);
                })
                .toList();
    }
}
