// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.yahoo.compress.IntegerCompressor;
import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Represent a query item matching the K nearest neighbors in a multidimensional vector space.
 * The query point vector is referenced by the name of a tensor passed as a query rank feature;
 * specifying "myvector" as the name means the query must set "ranking.features.query(myvector)".
 * This rank feature must be configured with the correct tensor type in the active query profile.
 * The field name (AKA the index name) given must be an attribute, with the exact same tensor type.
 *
 * @author arnej
 */
public class NearestNeighborItem extends SimpleTaggableItem {

    private int targetNumHits = 0;
    private int hnswExploreAdditionalHits = 0;
    private double distanceThreshold = Double.POSITIVE_INFINITY;
    private boolean approximate = true;
    private String field;
    private final String queryTensorName;
    private Double hnswApproximateThreshold = null;
    private Double hnswExplorationSlack = null;
    private Double hnswFilterFirstExploration = null;
    private Double hnswFilterFirstThreshold = null;
    private Double hnswPostFilterThreshold = null;
    private Double hnswTargetHitsMaxAdjustmentFactor = null;

    public NearestNeighborItem(String fieldName, String queryTensorName) {
        this.field = fieldName;
        this.queryTensorName = queryTensorName;
    }

    /** Returns the K number of hits to produce */
    public int getTargetNumHits() { return targetNumHits; }

    /** Returns the name of the index (field) to be searched */
    public String getIndexName() { return field; }

    /** Returns the distance threshold for nearest-neighbor hits */
    public double getDistanceThreshold () { return this.distanceThreshold ; }

    /** Returns the number of extra hits to explore in HNSW algorithm */
    public int getHnswExploreAdditionalHits() { return hnswExploreAdditionalHits; }

    /** Returns whether approximation is allowed */
    public boolean getAllowApproximate() { return approximate; }

    /** Returns the name of the query tensor */
    public String getQueryTensorName() { return queryTensorName; }

    /** Returns the approximate threshold for HNSW */
    public Double getHnswApproximateThreshold() { return hnswApproximateThreshold; }

    /** Returns the exploration slack for HNSW */
    public Double getHnswExplorationSlack() { return hnswExplorationSlack; }

    /** Returns the filter-first exploration parameter for HNSW */
    public Double getHnswFilterFirstExploration() { return hnswFilterFirstExploration; }

    /** Returns the filter-first threshold for HNSW */
    public Double getHnswFilterFirstThreshold() { return hnswFilterFirstThreshold; }

    /** Returns the post-filter threshold for HNSW */
    public Double getHnswPostFilterThreshold() { return hnswPostFilterThreshold; }

    /** Returns the target hits max adjustment factor for HNSW */
    public Double getHnswTargetHitsMaxAdjustmentFactor() { return hnswTargetHitsMaxAdjustmentFactor; }

    /** Set the K number of hits to produce */
    public void setTargetNumHits(int target) { this.targetNumHits = target; }

    /** Set the distance threshold for nearest-neighbor hits */
    public void setDistanceThreshold(double threshold) { this.distanceThreshold = threshold; }

    /** Set the number of extra hits to explore in HNSW algorithm */
    public void setHnswExploreAdditionalHits(int num) { this.hnswExploreAdditionalHits = num; }

    /** Set whether approximation is allowed */
    public void setAllowApproximate(boolean value) { this.approximate = value; }

    /** Set the approximate threshold for HNSW */
    public void setHnswApproximateThreshold(Double value) { this.hnswApproximateThreshold = value; }

    /** Set the exploration slack for HNSW */
    public void setHnswExplorationSlack(Double value) { this.hnswExplorationSlack = value; }

    /** Set the filter-first exploration parameter for HNSW */
    public void setHnswFilterFirstExploration(Double value) { this.hnswFilterFirstExploration = value; }

    /** Set the filter-first threshold for HNSW */
    public void setHnswFilterFirstThreshold(Double value) { this.hnswFilterFirstThreshold = value; }

    /** Set the post-filter threshold for HNSW */
    public void setHnswPostFilterThreshold(Double value) { this.hnswPostFilterThreshold = value; }

    /** Set the target hits max adjustment factor for HNSW */
    public void setHnswTargetHitsMaxAdjustmentFactor(Double value) { this.hnswTargetHitsMaxAdjustmentFactor = value; }

    @Override
    public void setIndexName(String index) { this.field = index; }

    @Override
    public ItemType getItemType() { return ItemType.NEAREST_NEIGHBOR; }

    @Override
    public String getName() { return "NEAREST_NEIGHBOR"; }

    @Override
    public int getTermCount() { return 1; }

    @Override
    public int encode(ByteBuffer buffer) {
        super.encodeThis(buffer);
        putString(field, buffer);
        putString(queryTensorName, buffer);
        int approxNum = (approximate ? 1 : 0);
        IntegerCompressor.putCompressedPositiveNumber(targetNumHits, buffer);
        IntegerCompressor.putCompressedPositiveNumber(approxNum, buffer);
        IntegerCompressor.putCompressedPositiveNumber(hnswExploreAdditionalHits, buffer);
        buffer.putDouble(distanceThreshold);
        return 1;  // number of encoded stack dump items
    }

    @Override
    protected void appendBodyString(StringBuilder buffer) {
        buffer.append("{field=").append(field);
        buffer.append(",queryTensorName=").append(queryTensorName);
        buffer.append(",hnsw.exploreAdditionalHits=").append(hnswExploreAdditionalHits);
        buffer.append(",distanceThreshold=").append(distanceThreshold);
        buffer.append(",approximate=").append(approximate);
        buffer.append(",targetHits=").append(targetNumHits);
        if (hnswApproximateThreshold != null)
            buffer.append(",hnsw.approximateThreshold=").append(hnswApproximateThreshold);
        if (hnswExplorationSlack != null)
            buffer.append(",hnsw.explorationSlack=").append(hnswExplorationSlack);
        if (hnswFilterFirstExploration != null)
            buffer.append(",hnsw.filterFirstExploration=").append(hnswFilterFirstExploration);
        if (hnswFilterFirstThreshold != null)
            buffer.append(",hnsw.filterFirstThreshold=").append(hnswFilterFirstThreshold);
        if (hnswPostFilterThreshold != null)
            buffer.append(",hnsw.postFilterThreshold=").append(hnswPostFilterThreshold);
        if (hnswTargetHitsMaxAdjustmentFactor != null)
            buffer.append(",hnsw.targetHitsMaxAdjustmentFactor=").append(hnswTargetHitsMaxAdjustmentFactor);
        buffer.append("}");
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("field", field);
        discloser.addProperty("queryTensorName", queryTensorName);
        discloser.addProperty("hnsw.exploreAdditionalHits", hnswExploreAdditionalHits);
        discloser.addProperty("distanceThreshold", distanceThreshold);
        discloser.addProperty("approximate", approximate);
        discloser.addProperty("targetHits", targetNumHits);
        if (hnswApproximateThreshold != null)
            discloser.addProperty("hnsw.approximateThreshold", hnswApproximateThreshold);
        if (hnswExplorationSlack != null)
            discloser.addProperty("hnsw.explorationSlack", hnswExplorationSlack);
        if (hnswFilterFirstExploration != null)
            discloser.addProperty("hnsw.filterFirstExploration", hnswFilterFirstExploration);
        if (hnswFilterFirstThreshold != null)
            discloser.addProperty("hnsw.filterFirstThreshold", hnswFilterFirstThreshold);
        if (hnswPostFilterThreshold != null)
            discloser.addProperty("hnsw.postFilterThreshold", hnswPostFilterThreshold);
        if (hnswTargetHitsMaxAdjustmentFactor != null)
            discloser.addProperty("hnsw.targetHitsMaxAdjustmentFactor", hnswTargetHitsMaxAdjustmentFactor);
    }

    @Override
    public boolean equals(Object o) {
        if ( ! super.equals(o)) return false;
        NearestNeighborItem other = (NearestNeighborItem)o;
        if (this.targetNumHits != other.targetNumHits) return false;
        if (this.hnswExploreAdditionalHits != other.hnswExploreAdditionalHits) return false;
        if (this.distanceThreshold != other.distanceThreshold) return false;
        if (this.approximate != other.approximate) return false;
        if ( ! this.field.equals(other.field)) return false;
        if ( ! this.queryTensorName.equals(other.queryTensorName)) return false;
        if ( ! Objects.equals(this.hnswApproximateThreshold, other.hnswApproximateThreshold)) return false;
        if ( ! Objects.equals(this.hnswExplorationSlack, other.hnswExplorationSlack)) return false;
        if ( ! Objects.equals(this.hnswFilterFirstExploration, other.hnswFilterFirstExploration)) return false;
        if ( ! Objects.equals(this.hnswFilterFirstThreshold, other.hnswFilterFirstThreshold)) return false;
        if ( ! Objects.equals(this.hnswPostFilterThreshold, other.hnswPostFilterThreshold)) return false;
        if ( ! Objects.equals(this.hnswTargetHitsMaxAdjustmentFactor, other.hnswTargetHitsMaxAdjustmentFactor)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), targetNumHits, hnswExploreAdditionalHits,
                            distanceThreshold, approximate, field, queryTensorName,
                            hnswApproximateThreshold, hnswExplorationSlack,
                            hnswFilterFirstExploration, hnswFilterFirstThreshold,
                            hnswPostFilterThreshold, hnswTargetHitsMaxAdjustmentFactor);
    }

    @Override
    SearchProtocol.QueryTreeItem toProtobuf() {
        var builder = SearchProtocol.ItemNearestNeighbor.newBuilder();
        builder.setProperties(ToProtobuf.buildTermProperties(this, getIndexName()));
        builder.setQueryTensorName(queryTensorName);
        builder.setTargetNumHits(targetNumHits);
        builder.setAllowApproximate(approximate);
        builder.setExploreAdditionalHits(hnswExploreAdditionalHits);
        builder.setDistanceThreshold(distanceThreshold);
        if (hnswApproximateThreshold != null) {
            builder.setApproximateThreshold(hnswApproximateThreshold);
        }
        if (hnswExplorationSlack != null) {
            builder.setExplorationSlack(hnswExplorationSlack);
        }
        if (hnswFilterFirstExploration != null) {
            builder.setFilterFirstExploration(hnswFilterFirstExploration);
        }
        if (hnswFilterFirstThreshold != null) {
            builder.setFilterFirstThreshold(hnswFilterFirstThreshold);
        }
        if (hnswPostFilterThreshold != null) {
            builder.setPostFilterThreshold(hnswPostFilterThreshold);
        }
        if (hnswTargetHitsMaxAdjustmentFactor != null) {
            builder.setTargetHitsMaxAdjustmentFactor(hnswTargetHitsMaxAdjustmentFactor);
        }
        return SearchProtocol.QueryTreeItem.newBuilder()
                .setItemNearestNeighbor(builder.build())
                .build();
    }

}
