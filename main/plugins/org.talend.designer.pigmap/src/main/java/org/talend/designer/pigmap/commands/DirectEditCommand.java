// ============================================================================
//
// Copyright (C) 2006-2016 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.designer.pigmap.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import org.eclipse.emf.common.util.EList;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.commands.Command;
import org.talend.designer.gefabstractmap.part.directedit.DirectEditType;
import org.talend.designer.pigmap.model.emf.pigmap.AbstractNode;
import org.talend.designer.pigmap.model.emf.pigmap.Connection;
import org.talend.designer.pigmap.model.emf.pigmap.INodeConnection;
import org.talend.designer.pigmap.model.emf.pigmap.InputTable;
import org.talend.designer.pigmap.model.emf.pigmap.LookupConnection;
import org.talend.designer.pigmap.model.emf.pigmap.OutputTable;
import org.talend.designer.pigmap.model.emf.pigmap.PigMapData;
import org.talend.designer.pigmap.model.emf.pigmap.PigmapFactory;
import org.talend.designer.pigmap.model.emf.pigmap.TableNode;
import org.talend.designer.pigmap.parts.PigMapInputTablePart;
import org.talend.designer.pigmap.parts.PigMapOutputTablePart;
import org.talend.designer.pigmap.parts.PigMapTableNodePart;
import org.talend.designer.pigmap.ui.expressionutil.PigMapExpressionManager;
import org.talend.designer.pigmap.ui.expressionutil.TableEntryLocation;
import org.talend.designer.pigmap.util.PigMapUtil;

/**
 * DOC hcyi class global comment. Detailled comment
 */
public class DirectEditCommand extends Command {

    private AbstractNode model;

    private Object newValue;

    private DirectEditType type;

    private EditPart targetEditPart;

    private PigMapTableNodePart tableNodePart;

    private PigMapData mapperData;

    protected PigMapExpressionManager expressionManager = new PigMapExpressionManager();

    public DirectEditCommand() {

    }

    public DirectEditCommand(EditPart targetEditPart, AbstractNode model, DirectEditType type, Object newValue) {
        this.targetEditPart = targetEditPart;
        this.model = model;
        this.newValue = newValue;
        this.type = type;
    }

    @Override
    public void execute() {
        try {
            if (model != null) {
                if (DirectEditType.EXPRESSION.equals(type)) {
                    List<TableEntryLocation> matchedLocations = expressionManager.parseTableEntryLocation((String) newValue);
                    EList<? extends INodeConnection> connections = null;
                    //
                    if (targetEditPart != null && targetEditPart instanceof PigMapTableNodePart) {
                        tableNodePart = (PigMapTableNodePart) targetEditPart;
                        if (tableNodePart.getParent() instanceof PigMapOutputTablePart) {
                            connections = model.getIncomingConnections();
                        } else if (tableNodePart.getParent() instanceof PigMapInputTablePart) {
                            connections = ((TableNode) model).getLookupIncomingConnections();
                        }
                    }
                    List usefullConnections = new ArrayList();
                    mapperData = PigMapUtil.getPigMapData(model);
                    if (!matchedLocations.isEmpty()) {
                        for (int i = 0; i < matchedLocations.size(); i++) {
                            TableEntryLocation currentLocation = matchedLocations.get(i);
                            boolean found = false;
                            for (INodeConnection conn : connections) {
                                TableEntryLocation sourceLocation = null;
                                String temp = "";
                                if (conn.getSource() != null && conn.getSource() instanceof TableNode) {
                                    TableNode tableSourceNode = (TableNode) conn.getSource();
                                    if (tableSourceNode.eContainer() != null
                                            && tableSourceNode.eContainer() instanceof InputTable) {
                                        temp = ((InputTable) tableSourceNode.eContainer()).getName() + "."
                                                + conn.getSource().getName();
                                    }
                                    sourceLocation = expressionManager.parseTableEntryLocation(temp).get(0);
                                }
                                if (currentLocation.equals(sourceLocation)) {
                                    found = true;
                                    usefullConnections.add(conn);
                                    break;
                                }
                            }
                            if (!found) {
                                if (mapperData != null) {
                                    AbstractNode sourceNode = findConnectionSource(mapperData, currentLocation);
                                    if (sourceNode != null && model.eContainer() != null && sourceNode.eContainer() != null) {
                                        INodeConnection connection = null;
                                        if (model.eContainer() instanceof OutputTable) {
                                            connection = PigmapFactory.eINSTANCE.createConnection();
                                            sourceNode.getOutgoingConnections().add((Connection) connection);
                                            model.getIncomingConnections().add((Connection) connection);
                                        } else if (model.eContainer() instanceof InputTable
                                                && sourceNode.eContainer() instanceof InputTable) {
                                            TableNode source = (TableNode) sourceNode;
                                            connection = PigmapFactory.eINSTANCE.createLookupConnection();
                                            source.getLookupOutgoingConnections().add((LookupConnection) connection);
                                            ((TableNode) model).getLookupIncomingConnections().add((LookupConnection) connection);
                                        }
                                        connection.setSource(sourceNode);
                                        connection.setTarget(model);
                                        mapperData.getConnections().add(connection);
                                        usefullConnections.add(connection);
                                    }
                                }
                            }
                        }
                    } else {
                        if (!connections.isEmpty()) {
                            if (tableNodePart.getParent() instanceof PigMapOutputTablePart) {
                                PigMapUtil.detachConnectionsSouce(model, mapperData);
                                model.getIncomingConnections().clear();
                            } else if (tableNodePart.getParent() instanceof PigMapInputTablePart) {
                                PigMapUtil.detachLookupSource((TableNode) model, mapperData);
                                ((TableNode) model).getLookupIncomingConnections().clear();
                            }
                        }
                    }
                    List<INodeConnection> copyOfConnections = new ArrayList<INodeConnection>(connections);
                    copyOfConnections.removeAll(usefullConnections);
                    if (tableNodePart.getParent() instanceof PigMapOutputTablePart) {
                        for (INodeConnection connection : copyOfConnections) {
                            if (connection.getSource() != null) {
                                if (connection.getSource().getOutgoingConnections().contains(connection)) {
                                    connection.getSource().getOutgoingConnections().remove(connection);
                                    mapperData.getConnections().remove(connection);
                                }
                            }
                        }
                        model.getIncomingConnections().removeAll(copyOfConnections);

                    } else if (tableNodePart.getParent() instanceof PigMapInputTablePart) {
                        for (INodeConnection connection : copyOfConnections) {
                            if (connection.getSource() != null) {
                                if (((TableNode) connection.getSource()).getLookupOutgoingConnections().contains(connection)) {
                                    ((TableNode) connection.getSource()).getLookupOutgoingConnections().remove(connection);
                                    mapperData.getConnections().remove(connection);
                                }
                            }
                        }
                        ((TableNode) model).getLookupIncomingConnections().removeAll(copyOfConnections);
                    }

                    model.setExpression((String) newValue);
                } else if (DirectEditType.NODE_NAME.equals(type)) {
                    //
                }
            }

        } catch (PatternSyntaxException ex) {
            // Syntax error in the regular expression
        }
    }

    protected AbstractNode findConnectionSource(PigMapData mapperData, TableEntryLocation matchedLocation) {
        if (mapperData == null) {
            return null;
        }
        for (InputTable inputTable : mapperData.getInputTables()) {
            for (TableNode node : inputTable.getNodes()) {
                TableEntryLocation sourceLocation = expressionManager.parseTableEntryLocation(
                        inputTable.getName() + "." + node.getName()).get(0);
                if (matchedLocation.equals(sourceLocation)) {
                    return node;
                }
            }
        }
        return null;
    }
}
