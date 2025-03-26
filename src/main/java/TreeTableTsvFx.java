import javafx.application.Application;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyCode;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.FileNotFoundException;
import java.util.*;
import java.net.URI;
import java.nio.file.Path;

public class TreeTableTsvFx extends Application {
    private static String help = """
    Usage: java -jar FXTreeTableTSV.jar <delimiter> [<idColumn> <parentColumn>] [--column-types=<columnTypes>] <file>
        columnTypes: Optional. Comma-separated list of types (string, double, boolean, url). Default: all String
        idColumn, parentColumn: Optional pair. Column names where id of node and id of parent are specified. Default: first and last columns.
        file: Path to TSV file or '-' to read from STDIN
    Examples (Powershell syntax):
        java -jar .\\target\\JavaFXTreeTableTSV-0.1-shaded.jar `t myfile.tsv
        java -jar FXTreeTableTSV.jar `t id parentId myfile.tsv
        cat myfile.tsv | java -jar FXTreeTableTSV.jar --delimiter=`t --column-types=url,string,double,string -
        # Build and display a file tree:
        Get-ChildItem -Recurse | select @{n='id';e='FullName'},Length,Name,@{n='parentId';e={$_.Directory.fullname ?? $_.Parent.FullName}} | ConvertTo-Csv -delim "`t" -UseQuotes Never > ./delmefileTree.tsv
        java -jar JavaFXTreeTableTSV.jar `t --column-types=string,double,string,string delmefileTree.tsv
        
        @'
        id,name,parentId
        1,root,
        2,child,1
        3,alsoChild,1
        4,grandChild,2
        5,alsoGrandChild,2
        6,anotherRootForNoReason,
        7,childWithDanglingParentId,999
        '@ | java -jar JavaFXTreeTableTSV.jar , -
    GUI:
        Left/Right : collapse/expand selected node.
        Shift+Left/Right : collapse/expand selected node recursively.
        Double click on right border of column header : Fit column width to content.
        Click on column header : Sort ascending/descending/reset.
        Double click on url : Open url in browser.
        * (asterisk) : Expand all.
    """;

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
        LinkedList<String> args = new LinkedList<String>(params.getUnnamed());
        Map<String,String> argsNamed = params.getNamed();
        if (params.getRaw().size() < 2 || args.contains("--help")) {
            System.err.println(help);
            System.exit(1);
        }

        String filePath = args.removeLast();
        
        String delimiter = argsNamed.containsKey("delimiter") ? argsNamed.get("delimiter") : args.removeFirst();
        String idColumn = argsNamed.containsKey("id-column") ? argsNamed.get("id-column") : 
           args.size() > 0 ? args.removeFirst() : null;
        String parentColumn = argsNamed.containsKey("parent-column") ? argsNamed.get("parent-column") : 
            args.size() > 0 ? args.removeFirst() : null;
        
        String[] columnTypes = null;
        if (argsNamed.containsKey("column-types")) {
            columnTypes = argsNamed.get("column-types").split(",");
        }
         
        
        

        
        List<TreeNode> nodes = new ArrayList<>();
        String[] headers = null;

        // Read the file or STDIN
        List<String[]> data = new ArrayList<>();
        try (Scanner scanner = filePath.equals("-") ? new Scanner(System.in) : new Scanner(Path.of(filePath).toAbsolutePath().toFile())) {
            if (scanner.hasNextLine()) {
                headers = scanner.nextLine().split(delimiter, -1);
                columnTypes = new String[headers.length];
            }
            while (scanner.hasNextLine()) {
                data.add(scanner.nextLine().split(delimiter, -1));
            }
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + filePath);
            System.exit(1);
        }

        // Default id and parent id columns - first and last
        if (idColumn == null || parentColumn == null) {
            idColumn = headers[0];
            parentColumn = headers[headers.length-1];
        }

        // Fill column types if they're not provided
        for (int i = 0; i < columnTypes.length; i++) {
            if (columnTypes[i] == null) {
                // Find first non-empty value in this column
                final int i_ = i;
                String sample = data.stream()
                    .map(arr -> arr[i_])
                    .filter(val -> val != null && !val.isEmpty())
                    .findFirst().orElse("");
                
                // Determine type based on sample
                columnTypes[i] = sample.matches("^https?://.+") ? "url" : 
                                sample.matches("-?\\d+(\\.\\d+)?") ? "double" : 
                                "string";
            }
        }
        
        for (String[] values : data) {
            nodes.add(new TreeNode(headers, values, columnTypes));
        }

        // Build the tree structure
        Map<String, TreeItem<TreeNode>> nodeMap = new HashMap<>();
        TreeItem<TreeNode> root = new TreeItem<>(new TreeNode(headers, new String[headers.length], columnTypes));

        // Add all nodes
        for (TreeNode node : nodes) {
            TreeItem<TreeNode> treeItem = new TreeItem<>(node);
            nodeMap.put(((Property<?>) node.getProperty(idColumn)).getValue().toString(), treeItem);
        }

        // Add all parent-child relationships
        for (TreeNode node : nodes) {
            String nodeId = ((Property<?>) node.getProperty(idColumn)).getValue().toString();
            String parentId = ((Property<?>) node.getProperty(parentColumn)).getValue().toString();
            TreeItem<TreeNode> parent = null;
            if (parentId != null && !parentId.isEmpty()) {
                parent = nodeMap.get(parentId);
            }
            if (parent != null) {
                parent.getChildren().add(nodeMap.get(nodeId));
            } else {
                //System.out.println("Found a parentId without a row: "+parentId);
                // Dangling parentId's are treated as if they're empty
                // (nodes with such parentId are considered to be root)
                root.getChildren().add(nodeMap.get(nodeId));
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
								setText(""); // Render empty cells as
							} else if (Double.isNaN(item.doubleValue())) {
								setText(""); // Render NaN values as
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
        primaryStage.setTitle("TreeTableTsvFx");
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