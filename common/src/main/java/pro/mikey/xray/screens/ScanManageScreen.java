package pro.mikey.xray.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import pro.mikey.xray.core.scanner.BlockScanType;
import pro.mikey.xray.core.scanner.ScanStore;
import pro.mikey.xray.core.scanner.ScanType;
import pro.mikey.xray.screens.helpers.GuiBase;
import pro.mikey.xray.screens.helpers.ImageButton;
import pro.mikey.xray.screens.helpers.SupportButton;
import pro.mikey.xray.utils.Utils;
import pro.mikey.xray.Configuration;
import pro.mikey.xray.XRay;
import pro.mikey.xray.core.OutlineRender;
import pro.mikey.xray.core.ScanController;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ScanManageScreen extends GuiBase {
    private static final ResourceLocation CIRCLE = XRay.assetLocation("gui/circle.png");

    // Layout constants
    private static final int SCROLL_LIST_WIDTH = 230;
    private static final int SCROLL_LIST_HEIGHT = 155;
    private static final int SCROLL_LIST_OFFSET_X = 37;
    private static final int SEARCH_BOX_WIDTH = 228;
    private static final int SEARCH_BOX_HEIGHT = 18;

    // Button dimensions
    private static final int SIDEBAR_BUTTON_WIDTH = 120;
    private static final int SIDEBAR_BUTTON_HEIGHT = 20;
    private static final int SIDEBAR_BUTTON_X_OFFSET = 79;
    private static final int BOTTOM_BUTTON_LEFT_WIDTH = 60;
    private static final int BOTTOM_BUTTON_RIGHT_WIDTH = 59;
    private static final int BOTTOM_BUTTON_HEIGHT = 20;
    private static final int BOTTOM_BUTTON_SPACING = 62;

    // Y position offsets
    private static final int SEARCH_BOX_Y_OFFSET = -105;
    private static final int SEARCH_TEXT_Y_OFFSET = -101;
    private static final int ADD_BLOCK_Y_OFFSET = -75;
    private static final int ADD_HAND_Y_OFFSET = -55;
    private static final int ADD_LOOK_Y_OFFSET = -35;
    private static final int LAVA_TOGGLE_Y_OFFSET = -15;
    private static final int DISTANCE_BUTTON_Y_OFFSET = 10;
    private static final int OPACITY_BUTTON_Y_OFFSET = 35;
    private static final int BOTTOM_BUTTONS_Y_OFFSET = 60;
    private static final int SCROLL_LIST_Y_OFFSET = 10;

    // Opacity constants
    private static final int OPACITY_INCREMENT = 10;
    private static final int OPACITY_MAX_PLUS_INCREMENT = 110; // Max (100) + increment, for modulo wraparound
    private static final int OPACITY_MAX = 100;

    // Rendering constants
    private static final float HINT_TEXT_SCALE = 0.75f;
    private static final int HINT_TEXT_LINE_SPACING = 12;
    private static final int HINT_TEXT_Y_BASE = 120;
    private static final int HINT_TEXT_X_OFFSET = -140;
    private static final int HINT_TEXT_VERTICAL_OFFSET = -3;

    // Search box text offsets
    private static final int SEARCH_PLACEHOLDER_X_OFFSET = -143;
    private static final int SEARCH_PLACEHOLDER_Y_OFFSET = -101;

    private Button distButtons;
    private Button opacityButton;
    private EditBox search;
    public ItemRenderer render;

    private String lastSearch = "";

    private ScanEntryScroller scrollList;

    public ScanManageScreen() {
        super(true);
        this.setSideTitle(I18n.get("xray.single.tools"));

        ScanStore scanStore = ScanController.INSTANCE.scanStore;
        if (scanStore.categories().isEmpty()) {
            // If there are no categories, we need to create the default ones
            scanStore.createDefaultCategories();
        }
    }

    @Override
    public void init() {
        assert minecraft != null;
        if (minecraft.player == null) {
            return;
        }

        this.render = Minecraft.getInstance().getItemRenderer();
        this.children().clear();

        this.scrollList = new ScanEntryScroller(((getWidth() / 2) - (SCROLL_LIST_WIDTH / 2)) - SCROLL_LIST_OFFSET_X, getHeight() / 2 + SCROLL_LIST_Y_OFFSET, SCROLL_LIST_WIDTH, SCROLL_LIST_HEIGHT, this);
        addRenderableWidget(this.scrollList);

        this.search = new EditBox(getFontRender(), getWidth() / 2 - 150, getHeight() / 2 + SEARCH_BOX_Y_OFFSET, SEARCH_BOX_WIDTH, SEARCH_BOX_HEIGHT, Component.empty());
        this.search.setCanLoseFocus(true);
        addRenderableWidget(this.search);

        // side bar buttons
        addRenderableWidget(new SupportButtonInner((getWidth() / 2) + SIDEBAR_BUTTON_X_OFFSET, getHeight() / 2 + ADD_BLOCK_Y_OFFSET, SIDEBAR_BUTTON_WIDTH, SIDEBAR_BUTTON_HEIGHT, Component.translatable("xray.input.add"), "xray.tooltips.add_block", button -> {
            minecraft.setScreen(new FindBlockScreen());
        }));

        addRenderableWidget(new SupportButtonInner(getWidth() / 2 + SIDEBAR_BUTTON_X_OFFSET, getHeight() / 2 + ADD_HAND_Y_OFFSET, SIDEBAR_BUTTON_WIDTH, SIDEBAR_BUTTON_HEIGHT, Component.translatable("xray.input.add_hand"), "xray.tooltips.add_block_in_hand", button -> {
            ItemStack handItem = minecraft.player.getItemInHand(InteractionHand.MAIN_HAND);

            // Check if the hand item is a block or not
            if (!(handItem.getItem() instanceof BlockItem)) {
                minecraft.player.displayClientMessage(Component.literal("[XRay] " + Component.translatable("xray.message.invalid_hand", Utils.safeItemStackName(handItem).getString())), false);
                this.onClose();
                return;
            }

            minecraft.setScreen(new ScanConfigureScreen(((BlockItem) handItem.getItem()).getBlock(), ScanManageScreen::new));
        }));

        addRenderableWidget(new SupportButtonInner(getWidth() / 2 + SIDEBAR_BUTTON_X_OFFSET, getHeight() / 2 + ADD_LOOK_Y_OFFSET, SIDEBAR_BUTTON_WIDTH, SIDEBAR_BUTTON_HEIGHT, Component.translatable("xray.input.add_look"), "xray.tooltips.add_block_looking_at", button -> {
            Player player = minecraft.player;
            if (minecraft.level == null || player == null) {
                return;
            }

            try {
                Vec3 look = player.getLookAngle();
                Vec3 start = new Vec3(player.blockPosition().getX(), player.blockPosition().getY() + player.getEyeHeight(), player.blockPosition().getZ());
                Vec3 end = new Vec3(player.blockPosition().getX() + look.x * 100, player.blockPosition().getY() + player.getEyeHeight() + look.y * 100, player.blockPosition().getZ() + look.z * 100);

                ClipContext context = new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
                BlockHitResult result = minecraft.level.clip(context);

                if (result.getType() == HitResult.Type.BLOCK) {
                    Block lookingAt = minecraft.level.getBlockState(result.getBlockPos()).getBlock();

                    minecraft.setScreen(new ScanConfigureScreen(lookingAt, ScanManageScreen::new));
                } else {
                    player.displayClientMessage(Component.literal("[XRay] " + I18n.get("xray.message.nothing_infront")), false);
                    this.onClose();
                }
            } catch (NullPointerException ex) {
                player.displayClientMessage(Component.literal("[XRay] " + I18n.get("xray.message.thats_odd")), false);
                this.onClose();
            }
        }));

        addRenderableWidget(distButtons = new SupportButtonInner((getWidth() / 2) + SIDEBAR_BUTTON_X_OFFSET, getHeight() / 2 + LAVA_TOGGLE_Y_OFFSET, SIDEBAR_BUTTON_WIDTH, SIDEBAR_BUTTON_HEIGHT, Component.translatable("xray.input.show-lava", ScanController.INSTANCE.isLavaActive()), "xray.tooltips.show_lava", button -> {
            ScanController.INSTANCE.toggleLava();
            button.setMessage(Component.translatable("xray.input.show-lava", ScanController.INSTANCE.isLavaActive()));
        }));

        addRenderableWidget(distButtons = new SupportButtonInner((getWidth() / 2) + SIDEBAR_BUTTON_X_OFFSET, getHeight() / 2 + DISTANCE_BUTTON_Y_OFFSET, SIDEBAR_BUTTON_WIDTH, SIDEBAR_BUTTON_HEIGHT, Component.translatable("xray.input.distance", ScanController.INSTANCE.getVisualRadius()), "xray.tooltips.distance", button -> {
            ScanController.INSTANCE.incrementCurrentDist();
            button.setMessage(Component.translatable("xray.input.distance", ScanController.INSTANCE.getVisualRadius()));
        }));

        addRenderableWidget(opacityButton = new SupportButtonInner((getWidth() / 2) + SIDEBAR_BUTTON_X_OFFSET, getHeight() / 2 + OPACITY_BUTTON_Y_OFFSET, SIDEBAR_BUTTON_WIDTH, SIDEBAR_BUTTON_HEIGHT, Component.translatable("xray.input.outline_opacity", Configuration.INSTANCE.outlineOpacity.get()), "xray.tooltips.outline_opacity", button -> {
            int currentOpacity = Configuration.INSTANCE.outlineOpacity.get();
            int newOpacity = (currentOpacity + OPACITY_INCREMENT) % OPACITY_MAX_PLUS_INCREMENT; // 0, 10, 20, ..., 100, then back to 0
            Configuration.INSTANCE.outlineOpacity.set(newOpacity);
            button.setMessage(Component.translatable("xray.input.outline_opacity", Configuration.INSTANCE.outlineOpacity.get()));
            OutlineRender.requestedRefresh = true;
        }));

        addRenderableWidget(
            Button.builder(Component.translatable("xray.single.help"), button -> {
                minecraft.setScreen(new HelpScreen());
            })
                    .pos(getWidth() / 2 + SIDEBAR_BUTTON_X_OFFSET, getHeight() / 2 + BOTTOM_BUTTONS_Y_OFFSET)
                    .size(BOTTOM_BUTTON_LEFT_WIDTH, BOTTOM_BUTTON_HEIGHT)
                    .build()
        );

        addRenderableWidget(
                Button.builder(Component.translatable("xray.single.close"), button -> {
                    this.onClose();
                })
                        .pos((getWidth() / 2 + SIDEBAR_BUTTON_X_OFFSET) + BOTTOM_BUTTON_SPACING, getHeight() / 2 + BOTTOM_BUTTONS_Y_OFFSET)
                        .size(BOTTOM_BUTTON_RIGHT_WIDTH, BOTTOM_BUTTON_HEIGHT)
                        .build()
        );
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (!search.isFocused() && keyEvent.key() == XRay.OPEN_GUI_KEY.key.getValue()) {
            this.onClose();
            return true;
        }

        return super.keyPressed(keyEvent);
    }

    private void updateSearch() {
        if (lastSearch.equals(search.getValue())) {
            return;
        }

        this.scrollList.updateEntries();
        lastSearch = search.getValue();
    }

    @Override
    public void tick() {
        super.tick();

        updateSearch();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (search.mouseClicked(event, bl))
            this.setFocused(search);

        if (event.button() == 1 && distButtons.isMouseOver(event.x(), event.y())) {
            ScanController.INSTANCE.decrementCurrentDist();
            distButtons.setMessage(Component.translatable("xray.input.distance", ScanController.INSTANCE.getVisualRadius()));
            distButtons.playDownSound(Minecraft.getInstance().getSoundManager());
        }

        if (event.button() == 1 && opacityButton.isMouseOver(event.x(), event.y())) {
            int currentOpacity = Configuration.INSTANCE.outlineOpacity.get();
            int newOpacity = currentOpacity - OPACITY_INCREMENT;
            if (newOpacity < 0) {
                newOpacity = OPACITY_MAX;
            }
            Configuration.INSTANCE.outlineOpacity.set(newOpacity);
            opacityButton.setMessage(Component.translatable("xray.input.outline_opacity", Configuration.INSTANCE.outlineOpacity.get()));
            opacityButton.playDownSound(Minecraft.getInstance().getSoundManager());
            OutlineRender.requestedRefresh = true;
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public void renderExtra(GuiGraphics graphics, int x, int y, float partialTicks) {
        if (!search.isFocused() && search.getValue().isEmpty()) {
            graphics.drawString(getFontRender(), I18n.get("xray.single.search"), getWidth() / 2 + SEARCH_PLACEHOLDER_X_OFFSET, getHeight() / 2 + SEARCH_PLACEHOLDER_Y_OFFSET, Color.GRAY.getRGB());
        }

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(this.getWidth() / 2f + HINT_TEXT_X_OFFSET, ((this.getHeight() / 2f) + HINT_TEXT_VERTICAL_OFFSET) + HINT_TEXT_Y_BASE);
        pose.scale(HINT_TEXT_SCALE, HINT_TEXT_SCALE);
        graphics.drawString(this.font, Component.translatable("xray.tooltips.edit1"), 0, 0, Color.GRAY.getRGB());
        pose.translate(0, HINT_TEXT_LINE_SPACING);
        graphics.drawString(this.font, Component.translatable("xray.tooltips.edit2"), 0, 0, Color.GRAY.getRGB());
        pose.popMatrix();
    }

    @Override
    public void removed() {
        ScanController.INSTANCE.requestBlockFinder(true);
        super.removed();
    }

    static final class SupportButtonInner extends SupportButton {
        public SupportButtonInner(int widthIn, int heightIn, int width, int height, Component text, String i18nKey, OnPress onPress) {
            super(widthIn, heightIn, width, height, text, Component.translatable(i18nKey), onPress);
        }
    }

    class ScanEntryScroller extends ObjectSelectionList<ScanEntryScroller.ScanSlot> {
        static final int SLOT_HEIGHT = 35;
        static final int ROW_WIDTH = 215;
        static final int SCROLLBAR_OFFSET = 6;
        static final int LIST_OFFSET_X = 36;
        public ScanManageScreen parent;

        ScanEntryScroller(int x, int y, int width, int height, ScanManageScreen parent) {
            super(ScanManageScreen.this.minecraft, width - 2, height, (ScanManageScreen.this.height / 2) - (height / 2) + SCROLL_LIST_Y_OFFSET, SLOT_HEIGHT);
            this.parent = parent;
            this.setX((parent.getWidth() / 2) - (width / 2) - LIST_OFFSET_X);
            this.updateEntries();
        }

        @Override
        public int getRowWidth() {
            return ROW_WIDTH;
        }

        @Override
        protected int scrollBarX() {
            return this.getX() + this.getRowWidth() + SCROLLBAR_OFFSET;
        }

        public void setSelected(@Nullable ScanManageScreen.ScanEntryScroller.ScanSlot entry, MouseButtonEvent mouse) {
            if (entry == null)
                return;

            if (mouse.hasShiftDown()) {
                Minecraft.getInstance().setScreen(new ScanConfigureScreen(entry.entry, ScanManageScreen::new));
                return;
            }

            entry.entry.enabled = !entry.entry.enabled();
            ScanController.INSTANCE.scanStore.save();
        }

        void updateEntries() {
            this.clearEntries();

            var searchString = search == null ? "" : search.getValue().toLowerCase();

            ScanStore scanStore = ScanController.INSTANCE.scanStore;
            var entries = scanStore.categories().stream().findFirst();
            if (entries.isEmpty()) {
                return;
            }

            List<ScanType> scanTargets = new ArrayList<>(entries.get().entries());
            scanTargets.sort(Comparator.comparing(ScanType::order));

            for (ScanType category : scanTargets) {
                if (!searchString.isEmpty() && !category.name().toLowerCase().contains(searchString)) {
                    continue;
                }

                this.addEntry(new ScanSlot(category, this));
            }
        }

        public static class ScanSlot extends ObjectSelectionList.Entry<ScanSlot> {
            // Button and icon constants
            private static final int BUTTON_SIZE = 16;
            private static final int ICON_TEXT_X_OFFSET = 25;
            private static final int TITLE_Y_OFFSET = 7;
            private static final int STATUS_Y_OFFSET = 17;
            private static final int ICON_Y_OFFSET = 7;

            // Button positioning
            private static final int DELETE_BUTTON_RIGHT_MARGIN = 18;
            private static final int EDIT_BUTTON_RIGHT_MARGIN = 38;
            private static final int COLOR_CIRCLE_RIGHT_MARGIN = 60;
            private static final int COLOR_CIRCLE_OFFSET_INNER = 58;

            // Circle rendering
            private static final int CIRCLE_OUTER_SIZE = 14;
            private static final int CIRCLE_INNER_SIZE = 10;
            private static final int CIRCLE_OUTER_Y_OFFSET = 9;
            private static final int CIRCLE_INNER_Y_OFFSET = 7;

            // Color constants
            private static final int COLOR_CIRCLE_OUTER = 0x7F000000; // Semi-transparent black border
            private static final int COLOR_CIRCLE_MASK = 0xFF000000;  // Opaque black mask

            private final ScanType entry;
            private final ScanEntryScroller parent;
            private final ItemStack icon;
            private final Button editButton;
            private final Button deleteButton;

            ScanSlot(ScanType entry, ScanEntryScroller parent) {
                this.entry = entry;
                this.parent = parent;

                if (entry instanceof BlockScanType blockScanType) {
                    this.icon = new ItemStack(blockScanType.block);
                } else {
                    this.icon = ItemStack.EMPTY;
                }

                // Create edit button with vertical ellipsis character
                this.editButton = Button.builder(Component.literal("⋮"), button -> {
                    Minecraft.getInstance().setScreen(new ScanConfigureScreen(entry, ScanManageScreen::new));
                }).size(BUTTON_SIZE, BUTTON_SIZE).build();

                // Create delete button with trash icon
                this.deleteButton = ImageButton.builder(button -> {
                    // Remove the entry from the scan store
                    ScanController.INSTANCE.scanStore.removeEntry(entry);
                    // Refresh the list
                    parent.updateEntries();
                    // Clear VBOs to update rendering
                    OutlineRender.clearVBOs();
                })
                .size(BUTTON_SIZE, BUTTON_SIZE)
                .image(XRay.assetLocation("gui/trash.png"), BUTTON_SIZE, BUTTON_SIZE)
                .build();
            }

            @Override
            public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovering, float partialTicks) {
                Font font = Minecraft.getInstance().font;

                guiGraphics.drawString(font, this.entry.name(), this.getContentX() + ICON_TEXT_X_OFFSET, this.getContentY() + TITLE_Y_OFFSET, 0xFFFFFFFF);
                guiGraphics.drawString(font, this.entry.enabled() ? "Enabled" : "Disabled", this.getContentX() + ICON_TEXT_X_OFFSET, this.getContentY() + STATUS_Y_OFFSET, this.entry.enabled() ? Color.GREEN.getRGB() : Color.RED.getRGB());

                guiGraphics.renderItem(this.icon, this.getContentX(), this.getContentY() + ICON_Y_OFFSET);

                // Position and render the buttons
                int buttonY = (int) (this.getContentY() + (this.getHeight() / 2f) - (BUTTON_SIZE / 2));

                // Delete button (far right)
                this.deleteButton.setX((this.getContentX() + this.getWidth()) - DELETE_BUTTON_RIGHT_MARGIN);
                this.deleteButton.setY(buttonY);
                this.deleteButton.render(guiGraphics, mouseX, mouseY, partialTicks);

                // Edit button (left of delete)
                this.editButton.setX((this.getContentX() + this.getWidth()) - EDIT_BUTTON_RIGHT_MARGIN);
                this.editButton.setY(buttonY);
                this.editButton.render(guiGraphics, mouseX, mouseY, partialTicks);

                // Color circle preview (moved left to make room for buttons)
                var stack = guiGraphics.pose();
                stack.pushMatrix();

                guiGraphics.blit(RenderPipelines.GUI_TEXTURED, ScanManageScreen.CIRCLE, (this.getContentX() + this.getWidth()) - COLOR_CIRCLE_RIGHT_MARGIN, (int) (this.getContentY() + (this.getHeight() / 2f) - CIRCLE_OUTER_Y_OFFSET), 0, 0, CIRCLE_OUTER_SIZE, CIRCLE_OUTER_SIZE, CIRCLE_OUTER_SIZE, CIRCLE_OUTER_SIZE, COLOR_CIRCLE_OUTER);
                guiGraphics.blit(RenderPipelines.GUI_TEXTURED, ScanManageScreen.CIRCLE, (this.getContentX() + this.getWidth()) - COLOR_CIRCLE_OFFSET_INNER, (int) (this.getContentY() + (this.getHeight() / 2f) - CIRCLE_INNER_Y_OFFSET), 0, 0, CIRCLE_INNER_SIZE, CIRCLE_INNER_SIZE, CIRCLE_INNER_SIZE, CIRCLE_INNER_SIZE, COLOR_CIRCLE_MASK | this.entry.colorInt());

                stack.popMatrix();
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent mouse, boolean bl) {
                // Check if click is on edit button
                if (this.editButton.mouseClicked(mouse, bl)) {
                    return true;
                }

                // Check if click is on delete button
                if (this.deleteButton.mouseClicked(mouse, bl)) {
                    return true;
                }

                // Fall back to default behavior (toggle enable/disable, or shift+click to edit)
                this.parent.setSelected(this, mouse);
                return false;
            }

            @Override
            public @NotNull Component getNarration() {
                return Component.empty();
            }
        }
    }
}
