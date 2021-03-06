/*
 * #%L
 * lb-core
 * %%
 * Copyright (C) 2013 - 2015 Gareth Healy
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.garethahealy.camel.file.loadbalancer.core;

import java.io.File;
import java.util.Map;

import com.garethahealy.camel.file.loadbalancer.filter.PriorityFileFilter;
import com.garethahealy.camel.file.loadbalancer.filter.PriorityFileFilterFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoadBalancedFileComponent extends FileComponent {

    private static final Logger LOG = LoggerFactory.getLogger(LoadBalancedFileComponent.class);

    public LoadBalancedFileComponent(CamelContext context) {
        super(context);
        setEndpointClass(LoadBalancedFileEndpoint.class);
    }

    protected GenericFileEndpoint<File> buildFileEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        //Use the base camel code to create the endpoint config, and then dump it into our endpoint
        GenericFileEndpoint<File> camelFileEndpoint = super.buildFileEndpoint(uri, remaining, parameters);

        LoadBalancedFileEndpoint answer = new LoadBalancedFileEndpoint(uri, this);
        answer.setFile(new File(remaining));
        answer.setConfiguration(camelFileEndpoint.getConfiguration());

        return answer;
    }

    @Override
    protected void afterPropertiesSet(GenericFileEndpoint<File> endpoint) throws Exception {
        super.afterPropertiesSet(endpoint);

        if (endpoint instanceof LoadBalancedFileEndpoint) {
            LoadBalancedFileEndpoint lbEndpoint = (LoadBalancedFileEndpoint)endpoint;

            PriorityFileFilterFactory factory = lbEndpoint.getPriorityFileFilterFactory();
            if (factory == null) {
                throw new ResolveEndpointFailedException(lbEndpoint.getEndpointUri(), "PriorityFileFilterFactory is null");
            }

            updateFilter(lbEndpoint, factory);
            updateMaxMessagesPerPoll(lbEndpoint, factory);
            updateMove(lbEndpoint);
        }
    }

    private void updateFilter(LoadBalancedFileEndpoint lbEndpoint, PriorityFileFilterFactory factory) {
        //Update the filter with one thats been created by the factory
        if (lbEndpoint.getFilter() == null) {
            LOG.debug("Updating Filter as not set");

            lbEndpoint.setFilter(factory.get());
        } else {
            throw new ResolveEndpointFailedException(lbEndpoint.getEndpointUri(), "Filter is set, which stops the PriorityFileFilterFactory overriding");
        }

    }

    private void updateMaxMessagesPerPoll(LoadBalancedFileEndpoint lbEndpoint, PriorityFileFilterFactory factory) {
        //Update the MaxMessagesPerPoll to match the amount of watches, so we don't get competing files
        if (lbEndpoint.getMaxMessagesPerPoll() <= 0) {
            LOG.debug("Updating MaxMessagesPerPoll from '{}' to '{}'", lbEndpoint.getMaxMessagesPerPoll(), factory.getAmountOfWatchers());

            lbEndpoint.setMaxMessagesPerPoll(factory.getAmountOfWatchers());
        } else {
            String message = String.format("MaxMessagesPerPoll is set as '%s' which does not match the amount of watchers '%s'", lbEndpoint.getMaxMessagesPerPoll(),
                                           factory.getAmountOfWatchers());

            throw new ResolveEndpointFailedException(lbEndpoint.getEndpointUri(), message);
        }
    }

    private void updateMove(LoadBalancedFileEndpoint lbEndpoint) {
        //Update Move so we can easily track whats files were handled by what endpoint
        if (lbEndpoint.getFilter() instanceof PriorityFileFilter) {
            PriorityFileFilter filter = (PriorityFileFilter)lbEndpoint.getFilter();

            //Set the move directory to contain the priority so its easy to distinguish what moved the file
            String currentMove = lbEndpoint.getMove() == null ? "" : lbEndpoint.getMove().toString();
            String move = ".camel";
            if (currentMove.length() > 0) {
                String[] moveSplit = currentMove.split("/");
                if (moveSplit.length == 3) {
                    move = moveSplit[1];
                }
            }

            LOG.debug("Updating Move from '{}' to '{}'", currentMove, move + filter.getPriority());

            lbEndpoint.setMove(move + filter.getPriority());
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .toString();
    }
}
