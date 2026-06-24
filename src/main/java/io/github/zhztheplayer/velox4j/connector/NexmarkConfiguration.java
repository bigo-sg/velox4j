/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package io.github.zhztheplayer.velox4j.connector;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NexmarkConfiguration implements Serializable {
  private final long numEvents;
  private final int numEventGenerators;
  private final String rateShape;
  private final int firstEventRate;
  private final int nextEventRate;
  private final String rateUnit;
  private final int ratePeriodSec;
  private final int preloadSeconds;
  private final int streamTimeout;
  private final boolean isRateLimited;
  private final boolean useWallclockEventTime;
  private final int personProportion;
  private final int auctionProportion;
  private final int bidProportion;
  private final int avgPersonByteSize;
  private final int avgAuctionByteSize;
  private final int avgBidByteSize;
  private final int hotAuctionRatio;
  private final int hotSellersRatio;
  private final int hotBiddersRatio;
  private final long windowSizeSec;
  private final long windowPeriodSec;
  private final long watermarkHoldbackSec;
  private final int numInFlightAuctions;
  private final int numActivePeople;
  private final long occasionalDelaySec;
  private final double probDelayedEvent;
  private final long outOfOrderGroupSize;

  @JsonCreator
  public NexmarkConfiguration(
      @JsonProperty("numEvents") long numEvents,
      @JsonProperty("numEventGenerators") int numEventGenerators,
      @JsonProperty("rateShape") String rateShape,
      @JsonProperty("firstEventRate") int firstEventRate,
      @JsonProperty("nextEventRate") int nextEventRate,
      @JsonProperty("rateUnit") String rateUnit,
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

  @JsonGetter
  public long getNumEvents() {
    return numEvents;
  }

  @JsonGetter
  public int getNumEventGenerators() {
    return numEventGenerators;
  }

  @JsonGetter
  public String getRateShape() {
    return rateShape;
  }

  @JsonGetter
  public int getFirstEventRate() {
    return firstEventRate;
  }

  @JsonGetter
  public int getNextEventRate() {
    return nextEventRate;
  }

  @JsonGetter
  public String getRateUnit() {
    return rateUnit;
  }

  @JsonGetter
  public int getRatePeriodSec() {
    return ratePeriodSec;
  }

  @JsonGetter
  public int getPreloadSeconds() {
    return preloadSeconds;
  }

  @JsonGetter
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

  @JsonGetter
  public int getPersonProportion() {
    return personProportion;
  }

  @JsonGetter
  public int getAuctionProportion() {
    return auctionProportion;
  }

  @JsonGetter
  public int getBidProportion() {
    return bidProportion;
  }

  @JsonGetter
  public int getAvgPersonByteSize() {
    return avgPersonByteSize;
  }

  @JsonGetter
  public int getAvgAuctionByteSize() {
    return avgAuctionByteSize;
  }

  @JsonGetter
  public int getAvgBidByteSize() {
    return avgBidByteSize;
  }

  @JsonGetter
  public int getHotAuctionRatio() {
    return hotAuctionRatio;
  }

  @JsonGetter
  public int getHotSellersRatio() {
    return hotSellersRatio;
  }

  @JsonGetter
  public int getHotBiddersRatio() {
    return hotBiddersRatio;
  }

  @JsonGetter
  public long getWindowSizeSec() {
    return windowSizeSec;
  }

  @JsonGetter
  public long getWindowPeriodSec() {
    return windowPeriodSec;
  }

  @JsonGetter
  public long getWatermarkHoldbackSec() {
    return watermarkHoldbackSec;
  }

  @JsonGetter
  public int getNumInFlightAuctions() {
    return numInFlightAuctions;
  }

  @JsonGetter
  public int getNumActivePeople() {
    return numActivePeople;
  }

  @JsonGetter
  public long getOccasionalDelaySec() {
    return occasionalDelaySec;
  }

  @JsonGetter
  public double getProbDelayedEvent() {
    return probDelayedEvent;
  }

  @JsonGetter
  public long getOutOfOrderGroupSize() {
    return outOfOrderGroupSize;
  }
}
