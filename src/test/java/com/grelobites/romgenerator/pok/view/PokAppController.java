package com.grelobites.romgenerator.pok.view;

import com.grelobites.romgenerator.pok.model.TrainerExporter;
import com.grelobites.romgenerator.pok.model.WinApeGame;
import com.grelobites.romgenerator.pok.model.WinApePoke;
import com.grelobites.romgenerator.pok.model.WinApePokeDatabase;
import com.grelobites.romgenerator.pok.model.WinApePokeValue;
import com.grelobites.romgenerator.pok.model.WinApeTrainer;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Shape;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class PokAppController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PokAppController.class);

    @FXML
    private TableView<WinApeGame> gameTable;

    @FXML
    private TableView<WinApeTrainer> trainerTable;

    @FXML
    private TableView<WinApePoke> pokeTable;

    @FXML
    private TextArea searchBox;

    @FXML
    private Button importButton;

    @FXML
    private TableColumn<WinApeGame, String> gameNameColumn;

    @FXML
    private TableColumn<WinApeTrainer, String> trainerDescriptionColumn;

    @FXML
    private TableColumn<WinApeTrainer, Shape> trainerStatusColumn;

    @FXML
    private TableColumn<WinApePoke, Shape> pokeStatusColumn;

    @FXML
    private TableColumn<WinApePoke, String> pokeAddressColumn;

    @FXML
    private TableColumn<WinApePoke, WinApePokeValue> pokeValueColumn;

    @FXML
    private Label selectedPokeComment;

    @FXML
    private Label selectedPokeSize;

    private ObservableList<WinApeGame> games = FXCollections.observableArrayList();
    private ObservableList<WinApeTrainer> trainers = FXCollections.observableArrayList();
    private TrainerExporter exporter = new TrainerExporter();

    private WinApePokeDatabase database;

    private static Shape getOKStatusShape() {
        return new Circle(5, Color.GREEN);
    }

    private static Shape getErrorStatusShape() {
        return new Circle(5, Color.RED);
    }

    private String parsePokeValue(WinApePoke poke) {
        return poke.getValue().render();
    }

    @FXML
    private void initialize() throws IOException {
        database = WinApePokeDatabase.fromInputStream(PokAppController.class
                .getResourceAsStream("/winape.pok"));

        gameNameColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        trainerDescriptionColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(cellData.getValue().getDescription()));
        pokeAddressColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(
                        String.format("0x%04x", cellData.getValue().getAddress())));

        pokeValueColumn.setEditable(true);
        pokeTable.getSelectionModel().cellSelectionEnabledProperty().set(true);

        pokeValueColumn.setCellValueFactory(
                cellData -> new SimpleObjectProperty<>(
                        cellData.getValue().getValue()));

        pokeStatusColumn.setCellValueFactory(
                cellData -> new SimpleObjectProperty<>(
                        cellData.getValue().getValue().exportable() ?
                                getOKStatusShape() : getErrorStatusShape())
                );

        trainerStatusColumn.setCellValueFactory(
                cellData -> new SimpleObjectProperty<>(
                        cellData.getValue().exportable() ?
                                getOKStatusShape() : getErrorStatusShape())
        );

        pokeValueColumn.setCellFactory(TextFieldTableCell.forTableColumn(
                new StringConverter<WinApePokeValue>() {
                    @Override
                    public String toString(WinApePokeValue value) {
                        return value.render();
                    }

                    @Override
                    public WinApePokeValue fromString(String value) {
                        return WinApePokeValue.fromString(value);
                    }
                }));

        pokeValueColumn.setOnEditStart(e -> {
           LOGGER.debug("On Edit Start {}", e);
        });

        pokeValueColumn.setOnEditCommit(e -> {
            LOGGER.debug("On Edit Commit {}", e);
            WinApePoke poke = e.getRowValue();
            if (poke.commitValues(e.getNewValue())) {
                LOGGER.debug("Values committed");
            } else {
                LOGGER.debug("Failure committing values");
            }
            e.getTableView().getItems().set(e.getTablePosition().getRow(), poke);
            e.getTableView().getSelectionModel().clearSelection();
            trainerTable.getItems().set(trainerTable.getSelectionModel().getSelectedIndex(),
                    trainerTable.getSelectionModel().getSelectedItem());

            LOGGER.debug("After edit the poke is {}", e.getRowValue());
        });

        games.setAll(database.games());
        gameTable.setItems(games);
        trainerTable.setItems(trainers);
        trainerTable.setPlaceholder(new Label("Select a Game"));
        pokeTable.setItems(exporter.getPokes());
        pokeTable.setPlaceholder(new Label("Select a Trainer"));

        gameTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> {
                    LOGGER.debug("New game selected: {}", newValue);
                    trainers.setAll(newValue.getTrainers());
                    exporter.bind(null);
                });
        trainerTable.selectionModelProperty().addListener(e -> {
            exporter.bind(null);
        });
        trainerTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> {
                    LOGGER.debug("New trainer selected: {}", newValue);
                    if (newValue != null) {
                        exporter.bind(newValue);
                        selectedPokeComment.textProperty().set(
                                newValue.getComment());
                    } else {
                        exporter.bind(null);
                        selectedPokeComment.textProperty().set("");
                    }
        });
        pokeTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> {
                    LOGGER.debug("New poke selected: {}", newValue);
                    if (newValue != null) {
                        selectedPokeSize.textProperty().set(
                                Integer.toString(newValue.getRequiredSize()));
                    } else {
                        selectedPokeSize.textProperty().set("-");
                    }

        });

        importButton.disableProperty().bind(
                trainerTable.getSelectionModel().selectedItemProperty().isNull()
            .or(Bindings.createBooleanBinding(() -> {
                WinApeTrainer trainer = trainerTable.getSelectionModel().selectedItemProperty().getValue();
                return trainer != null && trainer.exportable();
            }, trainerTable.getSelectionModel().selectedItemProperty())
                    .not()));

    }

}
