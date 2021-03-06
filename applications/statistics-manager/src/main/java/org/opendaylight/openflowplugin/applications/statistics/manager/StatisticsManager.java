/**
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.openflowplugin.applications.statistics.manager;

import java.util.List;
import java.util.UUID;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.controller.sal.binding.api.RpcConsumerRegistry;
import org.opendaylight.openflowplugin.applications.statistics.manager.StatPermCollector.StatCapabTypes;
import org.opendaylight.openflowplugin.applications.statistics.manager.impl.StatisticsManagerConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.meters.Meter;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.OpendaylightFlowStatisticsListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.table.statistics.rev131215.OpendaylightFlowTableStatisticsListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.transaction.rev150304.TransactionId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.port.rev130925.queues.Queue;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.statistics.rev131111.OpendaylightGroupStatisticsListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.meter.statistics.rev131111.OpendaylightMeterStatisticsListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.OpendaylightPortStatisticsListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.queue.statistics.rev131216.OpendaylightQueueStatisticsListener;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * statistics-manager
 * org.opendaylight.openflowplugin.applications.statistics.manager
 *
 * StatisticsManager
 * It represent a central point for whole module. Implementation
 * StatisticsManager registers all Operation/DS {@link StatNotifyCommiter} and
 * Config/DS {@link StatListeningCommiter}, as well as {@link StatPermCollector}
 * for statistic collecting and {@link StatRpcMsgManager} as Device RPCs provider.
 * In next, StatisticsManager provides all DS contact Transaction services.
 *
 * @author <a href="mailto:vdemcak@cisco.com">Vaclav Demcak</a>
 *
 * Created: Aug 27, 2014
 */
public interface StatisticsManager extends AutoCloseable, TransactionChainListener {

    /**
     * StatDataStoreOperation
     * Interface represent functionality to submit changes to DataStore.
     * Internal {@link TransactionChainListener} joining all DS commits
     * to Set of chained changes for prevent often DataStore touches.
     */
    abstract class StatDataStoreOperation {
        public enum StatsManagerOperationType {
            /**
             * Operation will carry out work related to new node addition /
             * update
             */
            NODE_UPDATE,
            /**
             * Operation will carry out work related to node removal
             */
            NODE_REMOVAL,
            /**
             * Operation will commit data to the operational data store
             */
            DATA_COMMIT_OPER_DS
        }

        private NodeId nodeId;
        private StatsManagerOperationType operationType = StatsManagerOperationType.DATA_COMMIT_OPER_DS;
        private UUID nodeUUID;

        public StatDataStoreOperation(final StatsManagerOperationType operType, final NodeId id){
            if(operType != null){
                operationType = operType;
            }
            nodeId = id;
            nodeUUID = generatedUUIDForNode();
        }

        public final StatsManagerOperationType getType() {
            return operationType;
        }

        public final NodeId getNodeId(){
            return nodeId;
        }

        public UUID getNodeUUID() {
            return nodeUUID;
        }

        /**
         * Apply all read / write (put|merge) operation for DataStore
         *
         * @param tx {@link ReadWriteTransaction}
         */
        public abstract void applyOperation(ReadWriteTransaction tx);

        protected abstract UUID generatedUUIDForNode();

        public InstanceIdentifier<Node> getNodeIdentifier() {
            final InstanceIdentifier<Node> nodeIdent = InstanceIdentifier.create(Nodes.class)
                    .child(Node.class, new NodeKey(nodeId));
            return nodeIdent;
        }

    }


    class Pair<L,R> {

        private final L left;
        private final R right;

        public Pair(L left, R right) {
            this.left = left;
            this.right = right;
        }

        public L getLeft() { return left; }
        public R getRight() { return right; }

        @Override
        public int hashCode() { return left.hashCode() ^ right.hashCode(); }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Pair)) return false;
            Pair pairo = (Pair) o;
            return this.left.equals(pairo.getLeft()) &&
                    this.right.equals(pairo.getRight());
        }

    }

    /**
     * Method starts whole StatisticManager functionality
     *
     * @param notifService
     * @param rpcRegistry
     */
    void start(final NotificationProviderService notifService,
            final RpcConsumerRegistry rpcRegistry);

    /**
     * Method provides read/write DataStore functionality cross applyOperation
     * defined in {@link StatDataStoreOperation}
     *
     * @param inventoryOper - operation for DataStore
     */
    void enqueue(final StatDataStoreOperation inventoryOper);

    /**
     * Method wraps {@link StatisticsManager#isProvidedFlowNodeActive(InstanceIdentifier)} method
     * to provide parallel statCollection process for Set of Nodes. So it has to
     * identify correct Node Set by NodeIdentifier
     *
     * @param nodeIdent
     */
     boolean isProvidedFlowNodeActive(InstanceIdentifier<Node> nodeIdent);

     /**
      * Method wraps {@link StatPermCollector}.collectNextStatistics to provide
      * parallel statCollection process for Set of Nodes. So it has to
      * identify correct Node Set by NodeIdentifier.
      *
      * @param nodeIdent
      */
     void collectNextStatistics(InstanceIdentifier<Node> nodeIdent, TransactionId xid);

     /**
      * Method wraps {@link StatPermCollector}.connectedNodeRegistration to provide
      * parallel statCollection process for Set of Nodes. So it has to
      * connect node to new or not full Node statCollector Set.
      *
      * @param nodeIdent
      * @param statTypes
      * @param nrOfSwitchTables
      */
     void connectedNodeRegistration(InstanceIdentifier<Node> nodeIdent,
             List<StatCapabTypes> statTypes, Short nrOfSwitchTables);

     /**
      * Method wraps {@link StatPermCollector}.disconnectedNodeUnregistration to provide
      * parallel statCollection process for Set of Nodes. So it has to identify
      * correct collector for disconnect node.
      *
      * @param nodeIdent
      */
     void disconnectedNodeUnregistration(InstanceIdentifier<Node> nodeIdent);

     /**
      * Method wraps {@link StatPermCollector}.registerAdditionalNodeFeature to provide
      * possibility to register additional Node Feature {@link StatCapabTypes} for
      * statistics collecting.
      *
      * @param nodeIdent
      * @param statCapab
      */
     void registerAdditionalNodeFeature(InstanceIdentifier<Node> nodeIdent, StatCapabTypes statCapab);

    /**
     * Method provides access to Device RPC methods by wrapped
     * internal method. In next {@link StatRpcMsgManager} is registered all
     * Multipart device msg response and joining all to be able run all
     * collected statistics in one time (easy identification Data for delete)
     *
     * @return {@link StatRpcMsgManager}
     */
    StatRpcMsgManager getRpcMsgManager();

    /**
     * Define Method : {@link org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode}
     * Operational/DS data change listener -&gt; impl. target -&gt; register FlowCapableNode to Statistic Collecting process
     * @return {@link StatNodeRegistration}
     */
    StatNodeRegistration getNodeRegistrator();

    /**
     * Define Method : Flow Config/DS data change listener -&gt; impl. target -&gt;
     * -&gt; make pair between Config/DS FlowId and Device Flow response Hash
     * @return
     */
    StatListeningCommiter<Flow, OpendaylightFlowStatisticsListener> getFlowListenComit();

    /**
     * Define Method : Meter Config/DS data change listener and Operation/DS notify commit
     * functionality
     * @return
     */
    StatListeningCommiter<Meter, OpendaylightMeterStatisticsListener> getMeterListenCommit();

    /**
     * Define Method : Group Config/DS data change listener and Operation/DS notify commit
     * functionality
     * @return
     */
    StatListeningCommiter<Group, OpendaylightGroupStatisticsListener> getGroupListenCommit();

    /**
     * Define Method : Queue Config/DS change listener and Operation/DS notify commit functionality
     * @return
     */
    StatListeningCommiter<Queue, OpendaylightQueueStatisticsListener> getQueueNotifyCommit();

    /**
     * Define Method : Table Operation/DS notify commit functionality
     * @return
     */
    StatNotifyCommiter<OpendaylightFlowTableStatisticsListener> getTableNotifCommit();

    /**
     * Define Method : Port Operation/DS notify commit functionality
     * @return
     */
    StatNotifyCommiter<OpendaylightPortStatisticsListener> getPortNotifyCommit();

    StatisticsManagerConfig getConfiguration();

    /**
     * A unique UUID is generated with each node added by the statistics manager implementation in order to uniquely
     * identify a session.
     * @param nodeInstanceIdentifier
     */
    UUID getGeneratedUUIDForNode(InstanceIdentifier<Node> nodeInstanceIdentifier);

}

