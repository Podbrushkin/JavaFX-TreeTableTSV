import javafx.application.Application;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.net.URI;

public class DynamicTreeTableExample extends Application {

    public static class TreeNode {
        private final Map<String, Property<?>> properties = new HashMap<>();

        public TreeNode(String[] headers, String[] values, String[] columnTypes) {
            for (int i = 0; i < headers.length; i++) {
                String header = headers[i];
                String value = values[i];
                String type = columnTypes[i];

                switch (type) {
                    case "double":
                        try {
							properties.put(header, new SimpleDoubleProperty(Double.parseDouble(value)));
						} catch (Exception e) {
							// Handle invalid or empty values as NaN
							properties.put(header, new SimpleDoubleProperty(Double.NaN));
						}
                        break;
                    case "boolean":
                        properties.put(header, new SimpleBooleanProperty(Boolean.parseBoolean(value)));
                        break;
                    case "url":
                        properties.put(header, new SimpleStringProperty(value));
                        break;
                    default: // string
                        properties.put(header, new SimpleStringProperty(value));
                        break;
                }
            }
        }

        // Dynamic getter for properties
        public Property<?> getProperty(String key) {
            return properties.get(key);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        // Parse command-line arguments
        Parameters params = getParameters();
        List<String> args = params.getRaw();
        if (args.size() < 5) {
            System.err.println("Usage: java DynamicTreeTableExample <idColumn> <parentOrChildColumn> <delimiter> <mode> <columnTypes> [file]");
            System.err.println("Mode: 'parent' for parentId, 'child' for childId");
            System.err.println("columnTypes: Comma-separated list of types (string, double, boolean, url)");
            System.err.println("file: Path to TSV file or '-' to read from STDIN");
            System.exit(1);
        }

        String idColumn = args.get(0);
        String parentOrChildColumn = args.get(1);
        String delimiter = args.get(2);
        String mode = args.get(3);
        String[] columnTypes = args.get(4).split(",");
        String filePath = args.size() > 5 ? args.get(5) : "-";

        if (!mode.equals("parent") && !mode.equals("child")) {
            System.err.println("Invalid mode. Use 'parent' or 'child'.");
            System.exit(1);
        }

        // Read the TSV file or STDIN
        List<TreeNode> nodes = new ArrayList<>();
        String[] headers = null;
        try (Scanner scanner = filePath.equals("-") ? new Scanner(System.in) : new Scanner(new File(filePath))) {
            if (scanner.hasNextLine()) {
                headers = scanner.nextLine().split(delimiter, -1); // Use -1 to avoid index issues
            }
            while (scanner.hasNextLine()) {
                String[] values = scanner.nextLine().split(delimiter, -1); // Use -1 to avoid index issues
                nodes.add(new TreeNode(headers, values, columnTypes));
            }
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + filePath);
            System.exit(1);
        }

        // Build the tree structure
        Map<String, TreeItem<TreeNode>> nodeMap = new HashMap<>();
        TreeItem<TreeNode> root = new TreeItem<>(new TreeNode(headers, new String[headers.length], columnTypes));

        for (TreeNode node : nodes) {
            TreeItem<TreeNode> treeItem = new TreeItem<>(node);
            nodeMap.put(((Property<?>) node.getProperty(idColumn)).getValue().toString(), treeItem);
        }

        if (mode.equals("parent")) {
            // ParentId mode: Each node specifies its parent
            for (TreeNode node : nodes) {
                String parentId = ((Property<?>) node.getProperty(parentOrChildColumn)).getValue().toString();
                if (parentId != null && !parentId.isEmpty()) {
                    TreeItem<TreeNode> parent = nodeMap.get(parentId);
                    if (parent != null) {
                        parent.getChildren().add(nodeMap.get(((Property<?>) node.getProperty(idColumn)).getValue().toString()));
                    }
                } else {
                    root.getChildren().add(nodeMap.get(((Property<?>) node.getProperty(idColumn)).getValue().toString()));
                }
            }
        } else {
            // ChildId mode: Each node specifies its children
            for (TreeNode node : nodes) {
                String childIds = ((Property<?>) node.getProperty(parentOrChildColumn)).getValue().toString();
                if (childIds != null && !childIds.isEmpty()) {
                    for (String childId : childIds.split(",")) {
                        TreeItem<TreeNode> child = nodeMap.get(childId.trim());
                        if (child != null) {
                            nodeMap.get(((Property<?>) node.getProperty(idColumn)).getValue().toString()).getChildren().add(child);
                        }
                    }
                }
            }
        }

        // Create the TreeTableView
        TreeTableView<TreeNode> treeTableView = new TreeTableView<>(root);
        treeTableView.setShowRoot(false);

        // Create columns dynamically based on headers
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i];
            String type = columnTypes[i];

            switch (type) {
                case "double":
                    TreeTableColumn<TreeNode, Double> doubleColumn = new TreeTableColumn<>(header);
                    doubleColumn.setCellValueFactory(param -> {
                        Property<?> property = param.getValue().getValue().getProperty(header);
                        return (ObservableValue<Double>) property;
                    });
                    doubleColumn.setSortable(true);
                    treeTableView.getColumns().add(doubleColumn);
                    break;
                case "boolean":
                    TreeTableColumn<TreeNode, Boolean> booleanColumn = new TreeTableColumn<>(header);
                    booleanColumn.setCellValueFactory(param -> {
                        Property<?> property = param.getValue().getValue().getProperty(header);
                        return (ObservableValue<Boolean>) property;
                    });
                    booleanColumn.setSortable(true);
                    treeTableView.getColumns().add(booleanColumn);
                    break;
                case "url":
                    TreeTableColumn<TreeNode, String> urlColumn = new TreeTableColumn<>(header);
                    urlColumn.setCellValueFactory(param -> {
                        Property<?> property = param.getValue().getValue().getProperty(header);
                        return (ObservableValue<String>) property;
                    });
                    urlColumn.setCellFactory(col -> new TreeTableCell<TreeNode, String>() {
                        @Override
                        protected void updateItem(String item, boolean empty) {
                            super.updateItem(item, empty);
                            if (item == null || empty) {
                                setText(null);
                            } else {
                                setText(item);
                                setOnMouseClicked(event -> {
                                    if (event.getClickCount() == 2) { // Double-click to open URL
                                        try {
                                            java.awt.Desktop.getDesktop().browse(new URI(item));
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                            }
                        }
                    });
                    urlColumn.setSortable(true);
                    treeTableView.getColumns().add(urlColumn);
                    break;
                default: // string
                    TreeTableColumn<TreeNode, String> stringColumn = new TreeTableColumn<>(header);
                    stringColumn.setCellValueFactory(param -> {
                        Property<?> property = param.getValue().getValue().getProperty(header);
                        return (ObservableValue<String>) property;
                    });
                    stringColumn.setSortable(true);
                    treeTableView.getColumns().add(stringColumn);
                    break;
            }
        }

        // Set up the scene
        StackPane rootPane = new StackPane();
        rootPane.getChildren().add(treeTableView);
        Scene scene = new Scene(rootPane, 800, 600);

        // Set up the stage
        primaryStage.setTitle("Dynamic TreeTable from TSV");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}