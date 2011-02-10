package synoptic.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import java.io.File;
import java.util.HashMap;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import org.apache.commons.collections15.Factory;
import org.apache.commons.collections15.Transformer;
import org.apache.commons.collections15.functors.MapTransformer;
import org.apache.commons.collections15.map.LazyMap;

import edu.uci.ics.jung.algorithms.layout.FRLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.visualization.GraphZoomScrollPane;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.EditingModalGraphMouse;
import edu.uci.ics.jung.visualization.control.ModalGraphMouse;
import edu.uci.ics.jung.visualization.renderers.BasicVertexLabelRenderer;
import edu.uci.ics.jung.visualization.renderers.Renderer.VertexLabel.Position;

import synoptic.model.Partition;
import synoptic.model.PartitionGraph;
import synoptic.model.interfaces.INode;
import synoptic.model.interfaces.ITransition;

/**
 * Shows how to create a graph editor with JUNG. Mouse modes and actions are
 * explained in the help text. The application version of GraphEditorDemo
 * provides a File menu with an option to save the visible graph as a jpeg file.
 * 
 * @author Tom Nelson Edits to display our graphs.
 */
public class JungGui extends JApplet implements Printable {

    private static final long serialVersionUID = -2023243689258876709L;

    /**
     * The partition graph maintained by Synoptic.
     */
    PartitionGraph pGraph;

    /**
     * The visual representation of pGraph, displayed by the Applet.
     */
    DirectedGraph<INode<Partition>, ITransition<Partition>> jGraph;

    /**
     * The layout used to display the graph.
     */
    Layout<INode<Partition>, ITransition<Partition>> layout;

    /**
     * the visual component and renderer for the graph
     */
    VisualizationViewer<INode<Partition>, ITransition<Partition>> vizViewer;

    static String instructions = "<html>"
            + "<h3>All Modes:</h3>"
            + "<ul>"
            + "<li>Right-click an empty area for <b>Create Vertex</b> popup"
            + "<li>Right-click on a Vertex for <b>Delete Vertex</b> popup"
            + "<li>Right-click on a Vertex for <b>Add Edge</b> menus <br>(if there are selected Vertices)"
            + "<li>Right-click on an Edge for <b>Delete Edge</b> popup"
            + "<li>Mousewheel scales with a crossover value of 1.0.<p>"
            + "     - scales the graph layout when the combined scale is greater than 1<p>"
            + "     - scales the graph view when the combined scale is less than 1"
            +

            "</ul>"
            + "<h3>Editing Mode:</h3>"
            + "<ul>"
            + "<li>Left-click an empty area to create a new Vertex"
            + "<li>Left-click on a Vertex and drag to another Vertex to create an Undirected Edge"
            + "<li>Shift+Left-click on a Vertex and drag to another Vertex to create a Directed Edge"
            + "</ul>"
            + "<h3>Picking Mode:</h3>"
            + "<ul>"
            + "<li>Mouse1 on a Vertex selects the vertex"
            + "<li>Mouse1 elsewhere unselects all Vertices"
            + "<li>Mouse1+Shift on a Vertex adds/removes Vertex selection"
            + "<li>Mouse1+drag on a Vertex moves all selected Vertices"
            + "<li>Mouse1+drag elsewhere selects Vertices in a region"
            + "<li>Mouse1+Shift+drag adds selection of Vertices in a new region"
            + "<li>Mouse1+CTRL on a Vertex selects the vertex and centers the display on it"
            + "<li>Mouse1 double-click on a vertex or edge allows you to edit the label"
            + "</ul>"
            + "<h3>Transforming Mode:</h3>"
            + "<ul>"
            + "<li>Mouse1+drag pans the graph"
            + "<li>Mouse1+Shift+drag rotates the graph"
            + "<li>Mouse1+CTRL(or Command)+drag shears the graph"
            + "<li>Mouse1 double-click on a vertex or edge allows you to edit the label"
            + "</ul>" + "<h3>Annotation Mode:</h3>" + "<ul>"
            + "<li>Mouse1 begins drawing of a Rectangle"
            + "<li>Mouse1+drag defines the Rectangle shape"
            + "<li>Mouse1 release adds the Rectangle as an annotation"
            + "<li>Mouse1+Shift begins drawing of an Ellipse"
            + "<li>Mouse1+Shift+drag defines the Ellipse shape"
            + "<li>Mouse1+Shift release adds the Ellipse as an annotation"
            + "<li>Mouse3 shows a popup to input text, which will become"
            + "<li>a text annotation on the graph at the mouse location"
            + "</ul>" + "</html>";

    /**
     * Creates a new JApplet based on a given PartitionGraph.
     * 
     * @param pGraph
     * @throws Exception
     */
    public JungGui(PartitionGraph pGraph) throws Exception {
        this.pGraph = pGraph;
        loadGraph();
        setUpGui();
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        addMenuBar(frame);
        frame.getContentPane().add(this);
        frame.pack();
        frame.setVisible(true);
    }

    class VertexFactory implements Factory<INode<Partition>> {
        @Override
        public INode<Partition> create() {
            return null;
        }
    }

    class EdgeFactory implements Factory<ITransition<Partition>> {
        @Override
        public ITransition<Partition> create() {
            return null;
        }
    }

    @SuppressWarnings("serial")
    public void addMenuBar(JFrame frame) {
        JMenu menu = new JMenu("File");
        menu.add(new AbstractAction("Make Image") {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                int option = chooser.showSaveDialog(JungGui.this);
                if (option == JFileChooser.APPROVE_OPTION) {
                    File file = chooser.getSelectedFile();
                    JungGui.this.writeJPEGImage(file);
                }
            }
        });
        menu.add(new AbstractAction("Print") {
            @Override
            public void actionPerformed(ActionEvent e) {
                PrinterJob printJob = PrinterJob.getPrinterJob();
                printJob.setPrintable(JungGui.this);
                if (printJob.printDialog()) {
                    try {
                        printJob.print();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(menu);
        frame.setJMenuBar(menuBar);
    }

    public void loadGraph() throws Exception {
        jGraph = new DirectedSparseGraph<INode<Partition>, ITransition<Partition>>();

        for (synoptic.model.interfaces.INode<Partition> node : pGraph
                .getNodes()) {
            jGraph.addVertex(node);
        }

        for (synoptic.model.interfaces.INode<Partition> node : pGraph
                .getNodes()) {
            for (ITransition<Partition> t : node.getTransitionsIterator()) {
                jGraph.addEdge(t, t.getSource(), t.getTarget(),
                        EdgeType.DIRECTED);
            }
        }
    }

    public void setUpGui() throws Exception {
        layout = new FRLayout<INode<Partition>, ITransition<Partition>>(jGraph);
        vizViewer = new VisualizationViewer<INode<Partition>, ITransition<Partition>>(
                layout);
        vizViewer.setBackground(Color.white);
        Transformer<INode<Partition>, String> labeller2 = new Transformer<INode<Partition>, String>() {
            @Override
            public String transform(INode<Partition> arg0) {
                return arg0.toStringConcise();
            }
        };

        vizViewer.getRenderContext().setVertexLabelTransformer(
                MapTransformer.<INode<Partition>, String> getInstance(LazyMap
                        .<INode<Partition>, String> decorate(
                                new HashMap<INode<Partition>, String>(),
                                labeller2)));

        Transformer<ITransition<Partition>, String> labeller = new Transformer<ITransition<Partition>, String>() {

            @Override
            public String transform(ITransition<Partition> arg0) {
                return arg0.toStringConcise();
            }
        };

        vizViewer
                .getRenderContext()
                .setEdgeLabelTransformer(
                        MapTransformer
                                .<ITransition<Partition>, String> getInstance(LazyMap
                                        .<ITransition<Partition>, String> decorate(
                                                new HashMap<ITransition<Partition>, String>(),
                                                labeller)));

        vizViewer.setVertexToolTipTransformer(vizViewer.getRenderContext()
                .getVertexLabelTransformer());
        vizViewer.getRenderContext().setVertexShapeTransformer(
                new Transformer<INode<Partition>, Shape>() {
                    @Override
                    public Shape transform(INode<Partition> arg0) {
                        return new Ellipse2D.Float(-10, -10, 70, 35);
                    }
                });

        vizViewer.getRenderContext().setVertexFillPaintTransformer(
                new Transformer<INode<Partition>, Paint>() {
                    @Override
                    public Paint transform(INode<Partition> arg0) {
                        return Color.WHITE;
                    }
                });

        vizViewer
                .getRenderer()
                .setVertexLabelRenderer(
                        new BasicVertexLabelRenderer<INode<Partition>, ITransition<Partition>>(
                                Position.CNTR));

        vizViewer.getRenderer().setVertexRenderer(new GuiVertex());

        Container content = getContentPane();
        final GraphZoomScrollPane panel = new GraphZoomScrollPane(vizViewer);
        content.add(panel);

        // Factory<INode<Partition>> vertexFactory = new VertexFactory();
        // Factory<ITransition<Partition>> edgeFactory = new EdgeFactory();

        final EditingModalGraphMouse<INode<Partition>, ITransition<Partition>> graphMouse = new EditingModalGraphMouse<INode<Partition>, ITransition<Partition>>(
                vizViewer.getRenderContext(), null, null);
        // vizViewer.getRenderContext(), vertexFactory, edgeFactory);

        // the EditingGraphMouse will pass mouse event coordinates to the
        // vertexLocations function to set the locations of the vertices as
        // they are created
        // graphMouse.setVertexLocations(vertexLocations);
        vizViewer.setGraphMouse(graphMouse);
        vizViewer.addKeyListener(graphMouse.getModeKeyListener());

        graphMouse.setMode(ModalGraphMouse.Mode.TRANSFORMING);

        // final ScalingControl scaler = new CrossoverScalingControl();
        JButton refineButton = new JButton("Refine");
        refineButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // TODO: perform one step of refinement on pGraph
                // scaler.scale(vizViewer, 1.1f, vizViewer.getCenter());

            }
        });
        JButton coarsenButton = new JButton("Coarsen");
        coarsenButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // TODO: perform one step of coarsening on pGraph
                // scaler.scale(vizViewer, 1 / 1.1f, vizViewer.getCenter());
            }
        });

        JButton help = new JButton("Help");
        help.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(vizViewer, instructions);
            }
        });

        JPanel controls = new JPanel();
        controls.add(refineButton);
        controls.add(coarsenButton);
        JComboBox modeBox = graphMouse.getModeComboBox();
        controls.add(modeBox);
        LayoutChooser.addLayoutCombo(controls, jGraph, vizViewer);
        controls.add(help);
        content.add(controls, BorderLayout.SOUTH);
    }

    /**
     * copy the visible part of the graph to a file as a jpeg image
     * 
     * @param file
     */
    public void writeJPEGImage(File file) {
        int width = vizViewer.getWidth();
        int height = vizViewer.getHeight();

        BufferedImage bi = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = bi.createGraphics();
        vizViewer.paint(graphics);
        graphics.dispose();

        try {
            ImageIO.write(bi, "jpeg", file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int print(java.awt.Graphics graphics,
            java.awt.print.PageFormat pageFormat, int pageIndex)
            throws java.awt.print.PrinterException {
        if (pageIndex > 0) {
            return Printable.NO_SUCH_PAGE;
        } else {
            java.awt.Graphics2D g2d = (java.awt.Graphics2D) graphics;
            vizViewer.setDoubleBuffered(false);
            g2d.translate(pageFormat.getImageableX(),
                    pageFormat.getImageableY());

            vizViewer.paint(g2d);
            vizViewer.setDoubleBuffered(true);

            return Printable.PAGE_EXISTS;
        }
    }
}
