package com.grelobites.romgenerator.model;

import com.grelobites.romgenerator.Constants;
import com.grelobites.romgenerator.util.ImageUtil;
import com.grelobites.romgenerator.util.Util;
import com.grelobites.romgenerator.util.compress.zx7.Zx7InputStream;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.List;

public class MLDGame extends BaseGame implements RamGame {
    private static final Logger LOGGER = LoggerFactory.getLogger(MLDGame.class);

    private HardwareMode hardwareMode;
    private Image screenshot;
    private MLDInfo mldInfo;
    private IntegerProperty size;

    public MLDGame(MLDInfo mldInfo, List<byte[]> data) {
        super(mldInfo.getGameType(), data);
        this.mldInfo = mldInfo;
        this.size = new SimpleIntegerProperty(super.getSize());
        hardwareMode = mldInfo.getHardwareMode();
    }

    @Override
    public boolean isCompressible() {
        return false;
    }

    public MLDInfo initializeMldInfo() {
        if (mldInfo == null) {
            mldInfo = MLDInfo.fromGameByteArray(data)
                    .orElseThrow(
                            ()-> new IllegalArgumentException("Unable to extract MLD data from file"));
        }
        return mldInfo;
    }

    public MLDInfo getMLDInfo() {
        return mldInfo;
    }

    public Image getScreenshot() {
        if (screenshot == null) {
            try {
                if (mldInfo.getCompressedScreenOffset() != 0) {
                    byte[] screenData = Util.fromInputStream(
                            new Zx7InputStream(
                                new ByteArrayInputStream(
                                    data.get(0),
                                    mldInfo.getCompressedScreenOffset(),
                                    mldInfo.getCompressedScreenSize()
                            )));
                    screenshot = ImageUtil
                            .scrLoader(ImageUtil.newScreenshot(),
                                    MLDInfo.MLD_DEFAULT_SCREENMODE,
                                    screenData,
                                    CrtcDisplayData.DEFAULT_VALUE,
                                    ImageUtil.embeddedPalette(screenData));


                }
            } catch (Exception e) {
                LOGGER.error("Loading screenshot", e);
            }
        }
        return screenshot;
    }

    @Override
    public HardwareMode getHardwareMode() {
        return hardwareMode;
    }

    @Override
    public void setHardwareMode(HardwareMode hardwareMode) {
        this.hardwareMode = hardwareMode;
    }

    @Override
    public void setScreenshot(Image screenshot) {
        this.screenshot = screenshot;
    }

    public MLDInfo getMldInfo() {
        return mldInfo;
    }

    public void setMldInfo(MLDInfo mldInfo) {
        this.mldInfo = mldInfo;
    }

    public int allocateSaveSpace(int saveSectorBase) {
        int headerSlot = mldInfo.getHeaderSlot();
        byte[] headerSlotData = getSlot(headerSlot);

        int base = MLDInfo.MLD_ALLOCATED_SECTORS_OFFSET;
        for (int i = 0; i < mldInfo.getRequiredSectors(); i++) {
            LOGGER.debug("Reserving MLD save sector to " + saveSectorBase);
            headerSlotData[base++] = (byte) saveSectorBase--;
        }
        return saveSectorBase;
    }

    public void reallocate(int slot) {
        LOGGER.debug("Relocating MLD game " + this + " with " + getSlotCount()
                + " slots to slot " + slot + ". Current base slot is "
                + mldInfo.getBaseSlot());

        int headerSlot = mldInfo.getHeaderSlot();
        byte[] headerSlotData = getSlot(headerSlot);
        headerSlotData[MLDInfo.MLD_HEADER_OFFSET] = (byte) slot;

        int tableSlot = mldInfo.getTableOffset() / Constants.SLOT_SIZE;
        int tableOffset = mldInfo.getTableOffset() % Constants.SLOT_SIZE;
        byte[] slotData = getSlot(tableSlot);
        for (int i = 0; i < mldInfo.getTableRows(); i++) {
            int offset = tableOffset + mldInfo.getRowSlotOffset();
            int correctedValue = slotData[offset] & 0x7F - mldInfo.getBaseSlot();
            int newValue = (correctedValue + slot) | (slotData[offset] & 0x80);
            LOGGER.debug("Patching slot " + tableSlot + " in position 0x"
                    + Integer.toHexString(offset & 0xffff) + " from value 0x"
                    + Integer.toHexString(slotData[offset] & 0xff)
                    + " to 0x" + Integer.toHexString(newValue & 0xff));
            slotData[offset] = (byte) newValue;

            tableOffset += mldInfo.getTableRowSize();
        }
        mldInfo.setBaseSlot(slot);
    }

    @Override
    public int getSize() {
        return size.get();
    }

    public IntegerProperty sizeProperty() {
        return size;
    }

    public void setSize(int size) {
        this.size.set(size);
    }

    @Override
    public String toString() {
        return "MLDGame{" +
                " name=" + name +
                ", hardwareMode=" + hardwareMode +
                ", mldInfo=" + mldInfo +
                '}';
    }

}
