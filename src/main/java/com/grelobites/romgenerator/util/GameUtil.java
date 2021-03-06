package com.grelobites.romgenerator.util;

import com.grelobites.romgenerator.Constants;
import com.grelobites.romgenerator.handlers.dandanatorcpc.DandanatorCpcConstants;
import com.grelobites.romgenerator.model.Game;
import com.grelobites.romgenerator.model.GameHeader;
import com.grelobites.romgenerator.model.SnapshotGame;
import com.grelobites.romgenerator.util.gameloader.GameImageLoader;
import com.grelobites.romgenerator.util.gameloader.GameImageLoaderFactory;
import com.grelobites.romgenerator.util.gameloader.GameImageType;
import com.grelobites.romgenerator.util.pokeimporter.ImportContext;
import com.grelobites.romgenerator.util.pokeimporter.PokeImporter;
import com.grelobites.romgenerator.util.pokeimporter.PokeImporterFactory;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.stream.IntStream;

public class GameUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameUtil.class);

    public static Optional<Game> createGameFromFile(File file) {
        LOGGER.debug("createGameFromFile " + file);
        if (file.canRead() && file.isFile()) {
            try (InputStream is = new FileInputStream(file)) {
                GameImageLoader loader = Util.getFileExtension(file.getName())
                        .map(GameImageLoaderFactory::getLoader)
                        .orElseGet(GameImageLoaderFactory::getDefaultLoader);

                Game game = loader.load(is);
                game.setName(getGameName(file));
                return Optional.of(game);
            } catch (Exception e) {
                LOGGER.error("Creating game from file " + file, e);
            }
        } else {
            LOGGER.warn("The provided item is not a file or not readable {}", file);
        }
        return Optional.empty();
    }

    public static String getGameName(File file) {
        String fileName = file.getName();
        int extensionIndex;
        if ((extensionIndex = fileName.lastIndexOf('.')) > 0) {
            fileName = fileName.substring(0, extensionIndex);

        }
        return filterGameName(fileName);
    }

    private static boolean allowedGameNameChar(Integer character) {
        return character > 31 && character < 127;
    }

    public static String filterGameName(String name) {
        return name.codePoints().map(c -> allowedGameNameChar(c) ? c : '_')
                .limit(DandanatorCpcConstants.GAMENAME_EFFECTIVE_SIZE)
                .collect(StringBuilder::new,
                        StringBuilder::appendCodePoint,
                        StringBuilder::append)
                .toString();
    }

    public static int getGamePokeSizeUsage(Game game) {
        if (game instanceof SnapshotGame) {
            SnapshotGame snapshotGame = (SnapshotGame) game;
            return snapshotGame.getTrainerList().getChildren().stream()
                    .flatMapToInt(g -> IntStream.builder()
                            .add(DandanatorCpcConstants.POKE_NAME_SIZE)
                            .add(1) //Number of pokes byte
                            .add(g.getChildren().size() * DandanatorCpcConstants.POKE_ENTRY_SIZE)
                            .build()).sum();
        } else {
            return 0;
        }
    }

    public static double getOverallPokeUsage(ObservableList<Game> gameList) {
        int usedSize = gameList.stream().mapToInt(GameUtil::getGamePokeSizeUsage).sum();
        int totalSize = DandanatorCpcConstants.POKE_ZONE_SIZE - DandanatorCpcConstants.POKE_HEADER_SIZE;
        LOGGER.debug("Used size is " + usedSize + " from " + totalSize);
        return ((double) usedSize) / totalSize;
    }

    public static void importPokesFromFile(SnapshotGame game, ImportContext ctx) throws IOException {
        PokeImporter importer = Util.getFileExtension(ctx.getPokesFile().getName())
                .map(PokeImporterFactory::getImporter)
                .orElseGet(PokeImporterFactory::getDefaultImporter);

        importer.importPokes(game.getTrainerList(), ctx);
    }

    public static void exportPokesToFile(SnapshotGame game, File pokeFile) throws IOException {
        PokeImporter importer = Util.getFileExtension(pokeFile.getName())
                .map(PokeImporterFactory::getImporter)
                .orElseGet(PokeImporterFactory::getDefaultImporter);
        try (FileOutputStream fos = new FileOutputStream(pokeFile)) {
            importer.exportPokes(game.getTrainerList(), fos);
        }
    }

    public static void exportGameAsSNA(Game selectedGame, File saveFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(saveFile)) {
            GameImageLoaderFactory.getLoader(GameImageType.SNA)
                    .save(selectedGame, fos);
        }
    }

    public static void exportGameAsRom(Game selectedGame, File saveFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(saveFile)) {
            fos.write(selectedGame.getSlot(0));
        }
    }

    public static boolean gameHasPokes(Game game) {
        return game instanceof SnapshotGame && ((SnapshotGame) game).hasPokes();
    }

    public static int getGameAddressValue(Game game, int address) {
        int slot = address / Constants.SLOT_SIZE;
        int offset = address % Constants.SLOT_SIZE;
        if (slot < game.getSlotCount()) {
            return Byte.toUnsignedInt(game.getSlot(slot)[offset]);
        } else {
            throw new IllegalArgumentException("Requesting past end address for game " + game.getName());
        }
    }

    public static int findInGameRam(SnapshotGame game, byte searchValue) {
        int offset = Constants.SLOT_SIZE;
        for (int i = 0; i < 3; i++) {
            int slot = game.getSlotForMappedRam(offset);
            byte[] data = game.getSlot(slot);
            for (byte memoryValue : data) {
                if (searchValue == memoryValue) {
                    return offset;
                }
                offset++;
            }
        }
        return -1;
    }

    public static boolean isSPValid(int sp) {
        return sp == 0 || sp > 0x4000 + 1;
    }

    private static int pushByte(SnapshotGame game, int value) {
        GameHeader header = game.getGameHeader();
        int sp = header.getSp();
        sp = sp == 0 ? 0xffff : sp - 1;
        byte[] spSlot = game.getSlot(game.getSlotForMappedRam(sp));
        int spOffset = sp % Constants.SLOT_SIZE;
        int currentValue = Byte.toUnsignedInt(spSlot[spOffset]);
        LOGGER.debug("Current value is 0x" + Integer.toHexString(currentValue));
        spSlot[spOffset] = (byte) (value & 0xff);
        header.setSp(sp);
        LOGGER.debug("Pushing byte 0x" + Integer.toHexString(value) + " into stack moved to 0x"
            + Integer.toHexString(sp));
        return currentValue;
    }

    private static int popByte(SnapshotGame game, Integer savedValue) {
        GameHeader header = game.getGameHeader();
        int sp = header.getSp();
        byte[] spSlot = game.getSlot(game.getSlotForMappedRam(sp));
        int spOffset = sp % Constants.SLOT_SIZE;
        int value = Byte.toUnsignedInt(spSlot[spOffset]);
        if (savedValue != null) {
            spSlot[spOffset] = savedValue.byteValue();
        }
        sp = sp == 0xffff ? 0 : sp + 1;
        LOGGER.debug("Popping byte from stack moved to 0x" + Integer.toHexString(sp));
        header.setSp(sp);
        return value;
    }


}
