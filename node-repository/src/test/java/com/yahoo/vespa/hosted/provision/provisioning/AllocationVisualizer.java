// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;

/**
 * @author smorgrav
 */
public class AllocationVisualizer extends JPanel {
    // Container box's width and height
    private static final int BOX_WIDTH = 1024;
    private static final int BOX_HEIGHT = 480;

    // node properties
    private int nodeWidth = BOX_WIDTH / 15;
    private int nodeHeight = nodeWidth / 2;
    private int nodeSpacing = nodeWidth / 3;

    private final List<AllocationSnapshot> steps;
    int step = 0;

    public AllocationVisualizer() {
        this(new ArrayList<>());
    }

    public AllocationVisualizer(List<AllocationSnapshot> steps) {
        this.steps = steps;
        this.setPreferredSize(new Dimension(BOX_WIDTH, BOX_HEIGHT));

        JButton back = new JButton("Back");
        back.addActionListener(e -> {
            if (step > 0) step -= 1;
            repaint();
        });
        JButton forward = new JButton("Forward");
        forward.addActionListener(e -> {
            if (step < steps.size() - 1) step += 1;
            repaint();
        });
        this.add(back);
        this.add(forward);
    }


    public void addStep(List<Node> nodes, String task, String message) {
        steps.add(new AllocationSnapshot(new NodeList(nodes), task, message));
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        System.out.println("PAINTING");
        if (steps.size() == 0) return;

        int nodeX = 40;
        int nodeY = BOX_HEIGHT - 20; //Start at the bottom

        // Draw the box
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, BOX_WIDTH, BOX_HEIGHT);

        // Find number of docker hosts (to calculate start, and width of each)
        // Draw the docker hosts - and color each container according to application
        AllocationSnapshot simStep = steps.get(step);
        NodeList hosts = simStep.nodes.nodeType(NodeType.host);
        for (Node host : hosts.asList()) {

            // Paint the host
            paintNode(host, g, nodeX, nodeY, true);

            // Paint containers
            NodeList containers = simStep.nodes.childrenOf(host);
            for (Node container : containers.asList()) {
                nodeY = paintNode(container, g, nodeX, nodeY, false);
            }

            // Next host
            nodeX += nodeWidth + nodeSpacing;
            nodeY = BOX_HEIGHT - 20;
        }

        // Display messages
        g.setColor(Color.BLACK);
        g.setFont(new Font("Courier New", Font.BOLD, 15));
        g.drawString(simStep.task, 20, 30);
        g.drawString(simStep.message, 20, 50);
    }

    private int paintNode(Node node, Graphics g, int x, int y, boolean isHost) {

        if (isHost) {
            g.setColor(Color.GRAY);
            for (int i = 0; i < node.flavor().getMinMainMemoryAvailableGb(); i++) {
                g.fillRect(x, y - nodeHeight, nodeWidth, nodeHeight);
                y = y - (nodeHeight + 2);
            }
        } else {
            g.setColor(Color.YELLOW);
            int multi = (int) node.flavor().getMinMainMemoryAvailableGb();
            int height = multi * nodeHeight + ((multi - 1) * 2);
            g.fillRect(x, y - height, nodeWidth, height);

            // Write tenant name in allocation
            String tenantName = node.allocation().get().owner().tenant().value();
            g.setColor(Color.BLACK);
            g.setFont(new Font("Courier New", Font.PLAIN, 12));
            g.drawString(tenantName, x + nodeWidth / 2 - 20, y - height / 2);

            y = y - height - 2;
        }
        return y;
    }

    public static void visualize(List<AllocationSnapshot> snaps) {
        AllocationVisualizer visualisator = new AllocationVisualizer(snaps);
        javax.swing.SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Allocation Simulator");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(visualisator);
            frame.pack();
            frame.setVisible(true);
        });

        while(true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
