/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.openflowplugin.impl.device;

import java.util.HashMap;
import java.util.Map;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.openflowplugin.api.openflow.connection.ConnectionContext;
import org.opendaylight.openflowplugin.impl.util.DeviceStateUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Martin Bobak &lt;mbobak@cisco.com&gt; on 2.6.2015.
 */
public class DeviceTransactionChainManagerProvider {


    private static final Logger LOG = LoggerFactory.getLogger(DeviceTransactionChainManagerProvider.class);
    private static final Map<NodeId, TransactionChainManager> txChManagers = new HashMap<>();

    public TransactionChainManager provideTransactionChainManagerOrWaitForNotification(final ConnectionContext connectionContext,
                                                                                       final DataBroker dataBroker,
                                                                                       final ReadyForNewTransactionChainHandler readyForNewTransactionChainHandler) {
        final NodeId nodeId = connectionContext.getNodeId();
        synchronized (this) {
            TransactionChainManager transactionChainManager = txChManagers.get(nodeId);
            if (null == transactionChainManager) {
                LOG.info("Creating new transaction chain for device {}", nodeId.toString());
                Registration registration = new Registration() {
                    @Override
                    public void close() throws Exception {
                        txChManagers.remove(nodeId);
                    }
                };
                transactionChainManager = new TransactionChainManager(dataBroker,
                        DeviceStateUtil.createNodeInstanceIdentifier(connectionContext.getNodeId()),
                        registration);
                synchronized (txChManagers) {
                    TransactionChainManager possibleValue = txChManagers.get(nodeId);
                    //some other thread managed to register its transaction chain manager before this block ended - drop this one
                    if (null == possibleValue) {
                        txChManagers.put(nodeId, transactionChainManager);
                    } else {
                        dropNewConnection(connectionContext, nodeId, transactionChainManager);
                    }
                }
                return transactionChainManager;
            } else {
                if (!transactionChainManager.attemptToRegisterHandler(readyForNewTransactionChainHandler)) {
                    dropNewConnection(connectionContext, nodeId, transactionChainManager);
                }
            }
        }

        return null;
    }

    private void dropNewConnection(final ConnectionContext connectionContext, final NodeId nodeId, final TransactionChainManager transactionChainManager) {
        LOG.info("There already exists one handler for connection described as {}. Transaction chain manager is in state {}. Will not try again.",
                nodeId,
                transactionChainManager.getTransactionChainManagerStatus());
        connectionContext.closeConnection(false);
    }


}
