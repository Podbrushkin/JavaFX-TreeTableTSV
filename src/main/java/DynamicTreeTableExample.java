import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.cell.TreeItemPropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class DynamicTreeTableExample extends Application {

    public static class TreeNode {
        private final Map<String, StringProperty> properties = new HashMap<>();

        public TreeNode(String[] headers, String[] values) {
            for (int i = 0; i < headers.length; i++) {
                properties.put(headers[i], new SimpleStringProperty(values[i]));
            }
        }

        // Dynamic getter for properties
        public StringProperty getProperty(String key) {
            return properties.get(key);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        // Parse command-line arguments
        Parameters params = getParameters();
        List<String> args = params.getRaw();
        if (args.size() < 4) {
            System.err.println("Usage: java DynamicTreeTableExample <tsvFile> <idColumn> <childIdColumn> <delimiter>");
            System.exit(1);
        }

        String tsvFile = args.get(0);
        String idColumn = args.get(1);
        String childIdColumn = args.get(2);
        String delimiter = args.get(3);

        // Read the TSV file
        List<TreeNode> nodes = new ArrayList<>();
        String[] headers = null;
        try (Scanner scanner = new Scanner(new File(tsvFile))) {
            if (scanner.hasNextLine()) {
                headers = scanner.nextLine().split(delimiter, -1);
            }
            while (scanner.hasNextLine()) {
                String[] values = scanner.nextLine().split(delimiter, -1);
                nodes.add(new TreeNode(headers, values));
            }
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + tsvFile);
            System.exit(1);
        }

        // Build the tree structure
        Map<String, TreeItem<TreeNode>> nodeMap = new HashMap<>();
        TreeItem<TreeNode> root = new TreeItem<>(new TreeNode(headers, new String[headers.length]));
        for (TreeNode node : nodes) {
            TreeItem<TreeNode> treeItem = new TreeItem<>(node);
            nodeMap.put(node.getProperty(idColumn).get(), treeItem);
        }
        for (TreeNode node : nodes) {
            String childId = node.getProperty(childIdColumn).get();
            if (childId != null && !childId.isEmpty()) {
                TreeItem<TreeNode> parent = nodeMap.get(childId);
                if (parent != null) {
                    parent.getChildren().add(nodeMap.get(node.getProperty(idColumn).get()));
                }
            } else {
                root.getChildren().add(nodeMap.get(node.getProperty(idColumn).get()));
            }
        }

        // Create the TreeTableView
        TreeTableView<TreeNode> treeTableView = new TreeTableView<>(root);
        treeTableView.setShowRoot(false);

        // Create columns dynamically based on headers
        for (String header : headers) {
            TreeTableColumn<TreeNode, String> column = new TreeTableColumn<>(header);
            column.setCellValueFactory(param -> param.getValue().getValue().getProperty(header));
            column.setSortable(true);
            treeTableView.getColumns().add(column);
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