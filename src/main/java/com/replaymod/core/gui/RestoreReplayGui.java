package com.replaymod.core.gui;

import com.google.common.io.Files;
import com.google.gson.Gson;
import com.replaymod.core.versions.MCVer;
import com.replaymod.replaystudio.io.ReplayInputStream;
import com.replaymod.replaystudio.io.ReplayOutputStream;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.replay.ReplayMetaData;
import com.replaymod.replaystudio.replay.ZipReplayFile;
import com.replaymod.replaystudio.studio.ReplayStudio;
import de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import de.johni0702.minecraft.gui.container.GuiPanel;
import de.johni0702.minecraft.gui.container.GuiScreen;
import de.johni0702.minecraft.gui.element.GuiButton;
import de.johni0702.minecraft.gui.element.GuiLabel;
import de.johni0702.minecraft.gui.layout.CustomLayout;
import de.johni0702.minecraft.gui.layout.HorizontalLayout;
import de.johni0702.minecraft.gui.layout.VerticalLayout;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import static com.replaymod.replaystudio.util.Utils.readInt;
import static com.replaymod.replaystudio.util.Utils.writeInt;

public class RestoreReplayGui extends AbstractGuiScreen<RestoreReplayGui> {

    public final GuiScreen parent;
    public final File file;
    public final GuiPanel textPanel = new GuiPanel().setLayout(new VerticalLayout().setSpacing(3));
    public final GuiPanel buttonPanel = new GuiPanel().setLayout(new HorizontalLayout().setSpacing(5));
    public final GuiPanel contentPanel = new GuiPanel(this).addElements(new VerticalLayout.Data(0.5),
            textPanel, buttonPanel).setLayout(new VerticalLayout().setSpacing(20));
    public final GuiButton yesButton = new GuiButton(buttonPanel).setSize(150, 20).setI18nLabel("gui.yes");
    public final GuiButton noButton = new GuiButton(buttonPanel).setSize(150, 20).setI18nLabel("gui.no");

    public RestoreReplayGui(GuiScreen parent, File file) {
        this.parent = parent;
        this.file = file;

        textPanel.addElements(new VerticalLayout.Data(0.5),
                    new GuiLabel().setI18nText("replaymod.gui.restorereplay1"),
                    new GuiLabel().setI18nText("replaymod.gui.restorereplay2", Files.getNameWithoutExtension(file.getName())),
                    new GuiLabel().setI18nText("replaymod.gui.restorereplay3"));
        yesButton.onClick(() -> {
            try {
                ReplayStudio studio = new ReplayStudio();
                ReplayFile replayFile = new ZipReplayFile(studio, null, file);
                // Commit all not-yet-committed files into the main zip file.
                // If we don't do this, then re-writing packet data below can actually overwrite uncommitted packet data!
                replayFile.save();
                ReplayMetaData metaData = replayFile.getMetaData();
                if (metaData != null && metaData.getDuration() == 0) {
                    // Try to restore replay duration
                    // We need to re-write the packet data in case there are any incomplete packets dangling at the end
                    try (ReplayInputStream in = replayFile.getPacketData(MCVer.getPacketTypeRegistry(true));
                         ReplayOutputStream out = replayFile.writePacketData()) {
                        while (true) {
                            // To prevent failing at un-parsable packets and to support recovery in minimal mode,
                            // we do not use the ReplayIn/OutputStream methods but instead parse the packets ourselves.
                            int time = readInt(in);
                            int length = readInt(in);
                            if (time == -1 || length == -1) {
                                break;
                            }
                            byte[] buf = new byte[length];
                            IOUtils.readFully(in, buf);

                            // Fully read, update replay duration
                            metaData.setDuration(time);

                            // Write packet back into recovered replay
                            writeInt(out, time);
                            writeInt(out, length);
                            out.write(buf);
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                    // Write back the actual duration
                    try (OutputStream out = replayFile.write("metaData.json")) {
                        metaData.setGenerator(metaData.getGenerator() + "(+ ReplayMod Replay Recovery)");
                        String json = (new Gson()).toJson(metaData);
                        out.write(json.getBytes());
                    }
                }
                replayFile.save();
                replayFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            parent.display();
        });
        noButton.onClick(() -> {
            try {
                File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
                File deleted = new File(file.getParentFile(), file.getName() + ".del");
                if (deleted.exists()) {
                    FileUtils.deleteDirectory(deleted);
                }
                Files.move(tmp, deleted);
            } catch (IOException e) {
                e.printStackTrace();
            }
            parent.display();
        });

        setLayout(new CustomLayout<RestoreReplayGui>() {
            @Override
            protected void layout(RestoreReplayGui container, int width, int height) {
                pos(contentPanel, width / 2 - width(contentPanel) / 2, height / 2 - height(contentPanel) / 2);
            }
        });
    }

    @Override
    protected RestoreReplayGui getThis() {
        return this;
    }
}
