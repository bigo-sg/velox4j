package io.github.zhztheplayer.velox4j.connector;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;


public class NexmarkConfiguration implements Serializable {
    public enum RateShape {
        SQUARE,
        SINE;
    }

    public enum RateUnit {
        PER_SECOND(1_000_000L),
        PER_MINUTE(60_000_000L);

        RateUnit(long usPerUnit) {
            this.usPerUnit = usPerUnit;
        }

        /** Number of microseconds per unit. */
        private final long usPerUnit;
    }

    @JsonProperty
    private final long numEvents;

    @JsonProperty
    private final int numEventGenerators;

    @JsonProperty
    private final RateShape rateShape;

    @JsonProperty
    private final int firstEventRate;

    @JsonProperty
    private final int nextEventRate;

    @JsonProperty
    private final RateUnit rateUnit;

    @JsonProperty
    private final int ratePeriodSec;

    @JsonProperty
    private final int preloadSeconds;

    @JsonProperty
    private final int streamTimeout;

    @JsonProperty
    private final boolean isRateLimited;

    @JsonProperty
    private final boolean useWallclockEventTime;

    @JsonProperty
    private final int personProportion;

    @JsonProperty
    private final int auctionProportion;

    @JsonProperty
    private final int bidProportion;

    @JsonProperty
    private final int avgPersonByteSize;

    @JsonProperty
    private final int avgAuctionByteSize;

    @JsonProperty
    private final int avgBidByteSize;

    @JsonProperty
    private final int hotAuctionRatio;

    @JsonProperty
    private final int hotSellersRatio;

    @JsonProperty
    private final int hotBiddersRatio;

    @JsonProperty
    private final long windowSizeSec;

    @JsonProperty
    private final long windowPeriodSec;

    @JsonProperty
    private final long watermarkHoldbackSec;

    @JsonProperty
    private final int numInFlightAuctions;

    @JsonProperty
    private final int numActivePeople;

    @JsonProperty
    private final long occasionalDelaySec;

    @JsonProperty
    private final double probDelayedEvent;

    @JsonProperty
    private final long outOfOrderGroupSize;

    @JsonCreator
    public NexmarkConfiguration(
            @JsonProperty("numEvents") long numEvents,
            @JsonProperty("numEventGenerators") int numEventGenerators,
            @JsonProperty("rateShape") NexmarkUtils.RateShape rateShape,
            @JsonProperty("firstEventRate") int firstEventRate,
            @JsonProperty("nextEventRate") int nextEventRate,
            @JsonProperty("rateUnit") NexmarkUtils.RateUnit rateUnit,
            @JsonProperty("ratePeriodSec") int ratePeriodSec,
            @JsonProperty("preloadSeconds") int preloadSeconds,
            @JsonProperty("streamTimeout") int streamTimeout,
            @JsonProperty("isRateLimited") boolean isRateLimited,
            @JsonProperty("useWallclockEventTime") boolean useWallclockEventTime,
            @JsonProperty("personProportion") int personProportion,
            @JsonProperty("auctionProportion") int auctionProportion,
            @JsonProperty("bidProportion") int bidProportion,
            @JsonProperty("avgPersonByteSize") int avgPersonByteSize,
            @JsonProperty("avgAuctionByteSize") int avgAuctionByteSize,
            @JsonProperty("avgBidByteSize") int avgBidByteSize,
            @JsonProperty("hotAuctionRatio") int hotAuctionRatio,
            @JsonProperty("hotSellersRatio") int hotSellersRatio,
            @JsonProperty("hotBiddersRatio") int hotBiddersRatio,
            @JsonProperty("windowSizeSec") long windowSizeSec,
            @JsonProperty("windowPeriodSec") long windowPeriodSec,
            @JsonProperty("watermarkHoldbackSec") long watermarkHoldbackSec,
            @JsonProperty("numInFlightAuctions") int numInFlightAuctions,
            @JsonProperty("numActivePeople") int numActivePeople,
            @JsonProperty("occasionalDelaySec") long occasionalDelaySec,
            @JsonProperty("probDelayedEvent") double probDelayedEvent,
            @JsonProperty("outOfOrderGroupSize") long outOfOrderGroupSize) {
        this.numEvents = numEvents;
        this.numEventGenerators = numEventGenerators;
        this.rateShape = rateShape;
        this.firstEventRate = firstEventRate;
        this.nextEventRate = nextEventRate;
        this.rateUnit = rateUnit;
        this.ratePeriodSec = ratePeriodSec;
        this.preloadSeconds = preloadSeconds;
        this.streamTimeout = streamTimeout;
        this.isRateLimited = isRateLimited;
        this.useWallclockEventTime = useWallclockEventTime;
        this.personProportion = personProportion;
        this.auctionProportion = auctionProportion;
        this.bidProportion = bidProportion;
        this.avgPersonByteSize = avgPersonByteSize;
        this.avgAuctionByteSize = avgAuctionByteSize;
        this.avgBidByteSize = avgBidByteSize;
        this.hotAuctionRatio = hotAuctionRatio;
        this.hotSellersRatio = hotSellersRatio;
        this.hotBiddersRatio = hotBiddersRatio;
        this.windowSizeSec = windowSizeSec;
        this.windowPeriodSec = windowPeriodSec;
        this.watermarkHoldbackSec = watermarkHoldbackSec;
        this.numInFlightAuctions = numInFlightAuctions;
        this.numActivePeople = numActivePeople;
        this.occasionalDelaySec = occasionalDelaySec;
        this.probDelayedEvent = probDelayedEvent;
        this.outOfOrderGroupSize = outOfOrderGroupSize;
    }

    @JsonGetter("numEvents")
    public long getNumEvents() {
        return numEvents;
    }

    @JsonGetter("numEventGenerators")
    public int getNumEventGenerators() {
        return numEventGenerators;
    }

    @JsonGetter("rateShape")
    public RateShape getRateShape() {
        return rateShape;
    }

    @JsonGetter("firstEventRate")
    public int getFirstEventRate() {
        return firstEventRate;
    }

    @JsonGetter("nextEventRate")
    public int getNextEventRate() {
        return nextEventRate;
    }

    @JsonGetter("rateUnit")
    public RateUnit getRateUnit() {
        return rateUnit;
    }

    @JsonGetter("ratePeriodSec")
    public int getRatePeriodSec() {
        return ratePeriodSec;
    }

    @JsonGetter("preloadSeconds")
    public int getPreloadSeconds() {
        return preloadSeconds;
    }

    @JsonGetter("streamTimeout")
    public int getStreamTimeout() {
        return streamTimeout;
    }

    @JsonGetter("isRateLimited")
    public boolean isRateLimited() {
        return isRateLimited;
    }

    @JsonGetter("useWallclockEventTime")
    public boolean isUseWallclockEventTime() {
        return useWallclockEventTime;
    }

    @JsonGetter("personProportion")
    public int getPersonProportion() {
        return personProportion;
    }

    @JsonGetter("auctionProportion")
    public int getAuctionProportion() {
        return auctionProportion;
    }

    @JsonGetter("bidProportion")
    public int getBidProportion() {
        return bidProportion;
    }

    @JsonGetter("avgPersonByteSize")
    public int getAvgPersonByteSize() {
        return avgPersonByteSize;
    }

    @JsonGetter("avgAuctionByteSize")
    public int getAvgAuctionByteSize() {
        return avgAuctionByteSize;
    }

    @JsonGetter("avgBidByteSize")
    public int getAvgBidByteSize() {
        return avgBidByteSize;
    }

    @JsonGetter("hotAuctionRatio")
    public int getHotAuctionRatio() {
        return hotAuctionRatio;
    }

    @JsonGetter("hotSellersRatio")
    public int getHotSellersRatio() {
        return hotSellersRatio;
    }

    @JsonGetter("hotBiddersRatio")
    public int getHotBiddersRatio() {
        return hotBiddersRatio;
    }

    @JsonGetter("windowSizeSec")
    public long getWindowSizeSec() {
        return windowSizeSec;
    }

    @JsonGetter("windowPeriodSec")
    public long getWindowPeriodSec() {
        return windowPeriodSec;
    }

    @JsonGetter("watermarkHoldbackSec")
    public long getWatermarkHoldbackSec() {
        return watermarkHoldbackSec;
    }

    @JsonGetter("numInFlightAuctions")
    public int getNumInFlightAuctions() {
        return numInFlightAuctions;
    }

    @JsonGetter("numActivePeople")
    public int getNumActivePeople() {
        return numActivePeople;
    }

    @JsonGetter("occasionalDelaySec")
    public long getOccasionalDelaySec() {
        return occasionalDelaySec;
    }

    @JsonGetter("probDelayedEvent")
    public double getProbDelayedEvent() {
        return probDelayedEvent;
    }

    @JsonGetter("outOfOrderGroupSize")
    public long getOutOfOrderGroupSize() {
        return outOfOrderGroupSize;
    }
}