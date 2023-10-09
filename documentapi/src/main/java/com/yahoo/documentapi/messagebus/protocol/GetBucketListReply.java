// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.BucketId;

import java.util.ArrayList;
import java.util.List;

public class GetBucketListReply extends DocumentReply {

    public static class BucketInfo {
        BucketId bucket;
        String bucketInformation;

        BucketInfo() {
            // must be deserialized into
        }

        public BucketInfo(BucketId bucket, String bucketInformation) {
            this.bucket = bucket;
            this.bucketInformation = bucketInformation;
        }

        public BucketId getBucketId() {
            return bucket;
        }

        public String getBucketInformation() {
            return bucketInformation;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof BucketInfo)) {
                return false;
            }
            BucketInfo rhs = (BucketInfo)obj;
            if (bucket == null) {
                if (rhs.bucket != null) {
                    return false;
                }
            } else if (!bucket.equals(rhs.bucket)) {
                return false;
            }
            if (bucketInformation == null) {
                if (rhs.bucketInformation != null) {
                    return false;
                }
            } else if (!bucketInformation.equals(rhs.bucketInformation)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(bucket, bucketInformation);
        }

        @Override
        public String toString() {
            return String.format("BucketInfo(%s: %s)", bucket, bucketInformation);
        }
    }

    private final List<BucketInfo> buckets = new ArrayList<BucketInfo>();

    public GetBucketListReply() {
        super(DocumentProtocol.REPLY_GETBUCKETLIST);
    }

    public List<BucketInfo> getBuckets() {
        return buckets;
    }
}
