/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.runners.core.construction;

import static org.junit.Assert.assertEquals;

import java.io.Serializable;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.DoFn.BoundedPerElement;
import org.apache.beam.sdk.transforms.DoFn.UnboundedPerElement;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.splittabledofn.HasDefaultTracker;
import org.apache.beam.sdk.transforms.splittabledofn.RestrictionTracker;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TupleTag;
import org.apache.beam.sdk.values.TupleTagList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link SplittableParDo}. */
@RunWith(JUnit4.class)
public class SplittableParDoTest {
  // ----------------- Tests for whether the transform sets boundedness correctly --------------
  private static class SomeRestriction
      implements Serializable, HasDefaultTracker<SomeRestriction, SomeRestrictionTracker> {
    @Override
    public SomeRestrictionTracker newTracker() {
      return new SomeRestrictionTracker(this);
    }
  }

  private static class SomeRestrictionTracker implements RestrictionTracker<SomeRestriction> {
    private final SomeRestriction someRestriction;

    public SomeRestrictionTracker(SomeRestriction someRestriction) {
      this.someRestriction = someRestriction;
    }

    @Override
    public SomeRestriction currentRestriction() {
      return someRestriction;
    }

    @Override
    public SomeRestriction checkpoint() {
      return someRestriction;
    }

    @Override
    public void checkDone() {}
  }

  @BoundedPerElement
  private static class BoundedFakeFn extends DoFn<Integer, String> {
    @ProcessElement
    public void processElement(ProcessContext context, SomeRestrictionTracker tracker) {}

    @GetInitialRestriction
    public SomeRestriction getInitialRestriction(Integer element) {
      return null;
    }
  }

  @UnboundedPerElement
  private static class UnboundedFakeFn extends DoFn<Integer, String> {
    @ProcessElement
    public void processElement(ProcessContext context, SomeRestrictionTracker tracker) {}

    @GetInitialRestriction
    public SomeRestriction getInitialRestriction(Integer element) {
      return null;
    }
  }

  private static PCollection<Integer> makeUnboundedCollection(Pipeline pipeline) {
    return pipeline
        .apply("unbounded", Create.of(1, 2, 3))
        .setIsBoundedInternal(PCollection.IsBounded.UNBOUNDED);
  }

  private static PCollection<Integer> makeBoundedCollection(Pipeline pipeline) {
    return pipeline
        .apply("bounded", Create.of(1, 2, 3))
        .setIsBoundedInternal(PCollection.IsBounded.BOUNDED);
  }

  private static final TupleTag<String> MAIN_OUTPUT_TAG = new TupleTag<String>() {};

  private ParDo.MultiOutput<Integer, String> makeParDo(DoFn<Integer, String> fn) {
    return ParDo.of(fn).withOutputTags(MAIN_OUTPUT_TAG, TupleTagList.empty());
  }

  @Rule
  public TestPipeline pipeline = TestPipeline.create();

  @Test
  public void testBoundednessForBoundedFn() {
    pipeline.enableAbandonedNodeEnforcement(false);

    DoFn<Integer, String> boundedFn = new BoundedFakeFn();
    assertEquals(
        "Applying a bounded SDF to a bounded collection produces a bounded collection",
        PCollection.IsBounded.BOUNDED,
        makeBoundedCollection(pipeline)
            .apply("bounded to bounded", new SplittableParDo<>(makeParDo(boundedFn)))
            .get(MAIN_OUTPUT_TAG)
            .isBounded());
    assertEquals(
        "Applying a bounded SDF to an unbounded collection produces an unbounded collection",
        PCollection.IsBounded.UNBOUNDED,
        makeUnboundedCollection(pipeline)
            .apply("bounded to unbounded", new SplittableParDo<>(makeParDo(boundedFn)))
            .get(MAIN_OUTPUT_TAG)
            .isBounded());
  }

  @Test
  public void testBoundednessForUnboundedFn() {
    pipeline.enableAbandonedNodeEnforcement(false);

    DoFn<Integer, String> unboundedFn = new UnboundedFakeFn();
    assertEquals(
        "Applying an unbounded SDF to a bounded collection produces a bounded collection",
        PCollection.IsBounded.UNBOUNDED,
        makeBoundedCollection(pipeline)
            .apply("unbounded to bounded", new SplittableParDo<>(makeParDo(unboundedFn)))
            .get(MAIN_OUTPUT_TAG)
            .isBounded());
    assertEquals(
        "Applying an unbounded SDF to an unbounded collection produces an unbounded collection",
        PCollection.IsBounded.UNBOUNDED,
        makeUnboundedCollection(pipeline)
            .apply("unbounded to unbounded", new SplittableParDo<>(makeParDo(unboundedFn)))
            .get(MAIN_OUTPUT_TAG)
            .isBounded());
  }
}
