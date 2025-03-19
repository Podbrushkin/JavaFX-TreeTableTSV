import javafx.application.Application;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyCode;
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
							// Parse the value as a Number (supports both integer and decimal)
							Number number = Double.parseDouble(value);
							properties.put(header, new SimpleObjectProperty<Number>(number));
						} catch (NumberFormatException | NullPointerException  e) {
							// Handle invalid or empty values as NaN
							properties.put(header, new SimpleObjectProperty<Number>(Double.NaN));
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
        List<String> args = params.getUnnamed();
        Map<String,String> argsNamed = params.getNamed();
        if (params.getRaw().size() < 2 || argsNamed.containsKey("--help")) {
            System.err.println("Usage: java DynamicTreeTableExample <delimiter> [<idColumn> <parentColumn>] [--column-types=<columnTypes>] <file>");
            System.err.println("columnTypes: Optional. Comma-separated list of types (string, double, boolean, url). Default: all String");
            System.err.println("idColumn, parentColumn: Optional pair. Column names where id of node and id of parent are specified. Default: first and last columns.");
            System.err.println("file: Path to TSV file or '-' to read from STDIN");
            System.err.println("Examples:");
            System.err.println("java -jar .\\target\\JavaFXTreeTableTSV-0.1-shaded.jar `t myfile.tsv");
            System.err.println("java -jar FXTreeTableTSV.jar `t id parentId myfile.tsv");
            System.err.println("cat myfile.tsv | java -jar FXTreeTableTSV.jar --delimiter=`t --column-types=url,string,double,string -");
            System.exit(1);
        }

        
        String delimiter = argsNamed.getOrDefault("delimiter", args.get(0));
        
        String idColumn = null;
        String parentColumn = null;
        if (args.size() >= 4) {
            idColumn = args.get(1);
            parentColumn = args.get(2);
        } else if (argsNamed.containsKey("id-column") && argsNamed.containsKey("parent-column")) {
            idColumn = argsNamed.get("id-column");
            parentColumn = argsNamed.get("parent-column");
        }
        String[] columnTypes = null;
        if (argsNamed.containsKey("column-types")) {
            columnTypes = argsNamed.get("column-types").split(",");
        }
         
        String filePath = args.get(args.size()-1);
        

        // Read the TSV file or STDIN
        List<TreeNode> nodes = new ArrayList<>();
        String[] headers = null;
        try (Scanner scanner = filePath.equals("-") ? new Scanner(System.in) : new Scanner(new File(filePath))) {
            if (scanner.hasNextLine()) {
                headers = scanner.nextLine().split(delimiter, -1);
                // if columnTypes haven't been provided by user, let them all be String
                if (columnTypes == null) {
                    columnTypes = "string,".repeat(headers.length).split(","); // genius
                }
                //System.out.println("columnTypes: "+Arrays.toString(columnTypes));
                if (idColumn == null || parentColumn == null) {
                    idColumn = headers[0];
                    parentColumn = headers[headers.length-1];
                }
            }
            while (scanner.hasNextLine()) {
                String[] values = scanner.nextLine().split(delimiter, -1);
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

        
        for (TreeNode node : nodes) {
            String parentId = ((Property<?>) node.getProperty(parentColumn)).getValue().toString();
            if (parentId != null && !parentId.isEmpty()) {
                TreeItem<TreeNode> parent = nodeMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(nodeMap.get(((Property<?>) node.getProperty(idColumn)).getValue().toString()));
                }
            } else {
                root.getChildren().add(nodeMap.get(((Property<?>) node.getProperty(idColumn)).getValue().toString()));
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
					TreeTableColumn<TreeNode, Number> doubleColumn = new TreeTableColumn<>(header);
					doubleColumn.setCellValueFactory(param -> {
						Property<?> property = param.getValue().getValue().getProperty(header);
						return (ObservableValue<Number>) property;
					});
					doubleColumn.setCellFactory(col -> new TreeTableCell<TreeNode, Number>() {
						@Override
						protected void updateItem(Number item, boolean empty) {
							super.updateItem(item, empty);
							if (empty || item == null) {
								setText(""); // Render empty cells as blank
							} else if (Double.isNaN(item.doubleValue())) {
								setText(""); // Render NaN values as "NaN"
							} else {
								// Display the number as-is (e.g., 10 instead of 10.0 for integers)
								if (item.doubleValue() == item.intValue()) {
									setText(String.valueOf(item.intValue())); // Display as integer
								} else {
									setText(String.valueOf(item.doubleValue())); // Display as double
								}
							}
						}
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
		// add shift+arrow deep expansion/collapse
		treeTableView.setOnKeyPressed(event -> {
            if (event.isShiftDown()) {
                if (event.getCode() == KeyCode.RIGHT) {
                    // Deep expand the selected row
                    TreeItem<TreeNode> selectedItem = treeTableView.getSelectionModel().getSelectedItem();
                    if (selectedItem != null) {
                        expandAll(selectedItem);
                    }
                    event.consume();
                } else if (event.getCode() == KeyCode.LEFT) {
                    // Deep collapse the selected row
                    TreeItem<TreeNode> selectedItem = treeTableView.getSelectionModel().getSelectedItem();
                    if (selectedItem != null) {
                        collapseAll(selectedItem);
                    }
                    event.consume();
                }
            }
        });

        // Set up the scene
        StackPane rootPane = new StackPane();
        rootPane.getChildren().add(treeTableView);
        Scene scene = new Scene(rootPane, 800, 600);

        // Set up the stage
        primaryStage.setTitle("Dynamic TreeTable from TSV");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
	// Recursively expand all nodes
    private void expandAll(TreeItem<?> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(true);
            for (TreeItem<?> child : item.getChildren()) {
                expandAll(child);
            }
        }
    }

    // Recursively collapse all nodes
    private void collapseAll(TreeItem<?> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(false);
            for (TreeItem<?> child : item.getChildren()) {
                collapseAll(child);
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}