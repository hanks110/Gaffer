/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.gchq.gaffer.accumulostore.operation.handler;

import uk.gov.gchq.gaffer.accumulostore.AccumuloStore;
import uk.gov.gchq.gaffer.accumulostore.key.IteratorSettingFactory;
import uk.gov.gchq.gaffer.accumulostore.key.exception.IteratorSettingException;
import uk.gov.gchq.gaffer.accumulostore.retriever.AccumuloRetriever;
import uk.gov.gchq.gaffer.accumulostore.retriever.impl.AccumuloSingleIDRetriever;
import uk.gov.gchq.gaffer.accumulostore.utils.AccumuloStoreConstants;
import uk.gov.gchq.gaffer.commonutil.iterable.CloseableIterable;
import uk.gov.gchq.gaffer.data.IsEdgeValidator;
import uk.gov.gchq.gaffer.data.TransformIterable;
import uk.gov.gchq.gaffer.data.element.Edge;
import uk.gov.gchq.gaffer.data.element.Element;
import uk.gov.gchq.gaffer.data.elementdefinition.view.View;
import uk.gov.gchq.gaffer.operation.OperationException;
import uk.gov.gchq.gaffer.operation.data.EntitySeed;
import uk.gov.gchq.gaffer.operation.impl.get.GetAdjacentEntitySeeds;
import uk.gov.gchq.gaffer.operation.impl.get.GetElements;
import uk.gov.gchq.gaffer.store.Context;
import uk.gov.gchq.gaffer.store.Store;
import uk.gov.gchq.gaffer.store.StoreException;
import uk.gov.gchq.gaffer.store.operation.handler.OutputOperationHandler;
import uk.gov.gchq.gaffer.user.User;
import java.util.Collections;

public class GetAdjacentEntitySeedsHandler implements OutputOperationHandler<GetAdjacentEntitySeeds, CloseableIterable<EntitySeed>> {

    @Override
    public CloseableIterable<EntitySeed> doOperation(final GetAdjacentEntitySeeds operation,
                                                     final Context context, final Store store)
            throws OperationException {
        return doOperation(operation, context.getUser(), (AccumuloStore) store);
    }

    public CloseableIterable<EntitySeed> doOperation(final GetAdjacentEntitySeeds op,
                                                     final User user,
                                                     final AccumuloStore store)
            throws OperationException {

        final AccumuloRetriever<?> edgeRetriever;
        try {
            final IteratorSettingFactory iteratorFactory = store.getKeyPackage().getIteratorFactory();
            final GetElements getEdges = new GetElements.Builder()
                    .options(op.getOptions())
                    .option(AccumuloStoreConstants.OPERATION_RETURN_MATCHED_SEEDS_AS_EDGE_SOURCE, "true")
                    .view(new View.Builder()
                            .merge(op.getView())
                            .entities(Collections.emptyMap())
                            .build())
                    .input(op.getInput())
                    .directedType(op.getDirectedType())
                    .inOutType(op.getIncludeIncomingOutGoing())
                    .build();

            edgeRetriever = new AccumuloSingleIDRetriever<>(store, getEdges, user, iteratorFactory.getElementPreAggregationFilterIteratorSetting(getEdges.getView(), store),
                    iteratorFactory.getElementPostAggregationFilterIteratorSetting(getEdges.getView(), store),
                    iteratorFactory.getEdgeEntityDirectionFilterIteratorSetting(getEdges),
                    iteratorFactory.getQueryTimeAggregatorIteratorSetting(getEdges.getView(), store));
        } catch (IteratorSettingException | StoreException e) {
            throw new OperationException(e.getMessage(), e);
        }

        return new ExtractDestinationEntitySeed(edgeRetriever);
    }

    private static final class ExtractDestinationEntitySeed extends TransformIterable<Element, EntitySeed> {
        private ExtractDestinationEntitySeed(final Iterable<Element> input) {
            super(input, new IsEdgeValidator());
        }

        @Override
        protected EntitySeed transform(final Element element) {
            return new EntitySeed(((Edge) element).getDestination());
        }

        @Override
        public void close() {
            ((CloseableIterable) super.getInput()).close();
        }
    }
}
