package dlx.thermalharvest.gui;

import cofh.core.gui.element.ElementButton;
import cofh.core.gui.element.ElementEnergyStored;
import cofh.core.gui.element.ElementFluidTank;
import cofh.core.gui.element.ElementSimple;
import cofh.core.util.helpers.StringHelper;
import cofh.thermalexpansion.gui.client.GuiPoweredBase;
import cofh.thermalexpansion.gui.element.ElementSlotOverlay;
import dlx.thermalharvest.ThermalHarvest;
import dlx.thermalharvest.block.TileHarvester;
import dlx.thermalharvest.render.ZoneRenderer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import java.util.List;

public class GuiHarvester extends GuiPoweredBase {

    public static final String TEX_PATH = ThermalHarvest.MOD_ID + ":textures/gui/harvester.png";
    public static final ResourceLocation TEXTURE = new ResourceLocation(TEX_PATH);

    private static final int AREA_X = 42;
    private static final int AREA_Y = 44;
    private static final int TANK_X = 152;
    private static final int TANK_Y = 9;

    private final TileHarvester myTile;
    private final ElementSlotOverlay[] outputOverlays = new ElementSlotOverlay[TileHarvester.OUTPUT_SLOTS];
    private ElementSlotOverlay fertilizerOverlay;      // синий: ввод всего
    private ElementSlotOverlay fertilizerOverlayPrimary; // зелёный: только удобрение
    private ElementSlotOverlay tankOverlay;            // синий
    private ElementSlotOverlay tankOverlaySecondary;   // фиолетовый: только вода
    private ElementSimple tankBackground;
    private ElementFluidTank tankElement;
    private ElementButton zoneButton;

    public GuiHarvester(InventoryPlayer inventory, TileEntity tile) {
        super(new ContainerHarvester(inventory, tile), tile, inventory.player, TEXTURE);
        generateInfo("tab.thermalharvest.harvester");
        myTile = (TileHarvester) tile;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Бак модуля орошения (виден только при установленном модуле)
        tankBackground = new ElementSimple(this, TANK_X - 1, TANK_Y - 1);
        tankBackground.setTextureOffsets(176, 0);
        tankBackground.setSize(18, 62);
        tankBackground.setTexture(TEX_PATH, 256, 256);
        addElement(tankBackground);

        // Подсветка ячеек при настройке сторон — стандартные элементы Thermal Expansion
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) {
                ElementSlotOverlay overlay = new ElementSlotOverlay(this,
                        ContainerHarvester.GRID_X + col * 18, ContainerHarvester.GRID_Y + row * 18);
                overlay.setSlotInfo(ElementSlotOverlay.SlotColor.ORANGE, ElementSlotOverlay.SlotType.STANDARD,
                        ElementSlotOverlay.SlotRender.FULL);
                outputOverlays[col + row * 5] = overlay;
                addElement(overlay);
            }
        }
        fertilizerOverlay = new ElementSlotOverlay(this, ContainerHarvester.FERT_X, ContainerHarvester.FERT_Y);
        fertilizerOverlay.setSlotInfo(ElementSlotOverlay.SlotColor.BLUE, ElementSlotOverlay.SlotType.STANDARD,
                ElementSlotOverlay.SlotRender.FULL);
        addElement(fertilizerOverlay);
        fertilizerOverlayPrimary = new ElementSlotOverlay(this, ContainerHarvester.FERT_X, ContainerHarvester.FERT_Y);
        fertilizerOverlayPrimary.setSlotInfo(ElementSlotOverlay.SlotColor.GREEN, ElementSlotOverlay.SlotType.STANDARD,
                ElementSlotOverlay.SlotRender.BOTTOM);
        addElement(fertilizerOverlayPrimary);

        tankOverlay = new ElementSlotOverlay(this, TANK_X, TANK_Y);
        tankOverlay.setSlotInfo(ElementSlotOverlay.SlotColor.BLUE, ElementSlotOverlay.SlotType.TANK,
                ElementSlotOverlay.SlotRender.FULL);
        addElement(tankOverlay);
        tankOverlaySecondary = new ElementSlotOverlay(this, TANK_X, TANK_Y);
        tankOverlaySecondary.setSlotInfo(ElementSlotOverlay.SlotColor.PURPLE, ElementSlotOverlay.SlotType.TANK,
                ElementSlotOverlay.SlotRender.BOTTOM);
        addElement(tankOverlaySecondary);

        tankElement = new ElementFluidTank(this, TANK_X, TANK_Y, myTile.getTank());
        tankElement.setGauge(0);
        tankElement.setAlwaysShow(true);
        addElement(tankElement);

        if (!myTile.smallStorage()) {
            addElement(new ElementEnergyStored(this, 8, 8, myTile.getEnergyStorage()));
        }
        addElement(new ElementNutrients(this, ContainerHarvester.FERT_X - 1, ContainerHarvester.FERT_Y + 19, myTile));

        zoneButton = new ElementButton(this, ContainerHarvester.FERT_X, 54, "zone", 176, 64, 192, 64, 16, 16, TEX_PATH);
        addElement(zoneButton);
        updateZoneButton();
    }

    private void updateZoneButton() {
        int mode = ZoneRenderer.getMode(myTile);
        switch (mode) {
            case ZoneRenderer.MODE_FARMLAND:
                zoneButton.setSheetX(208);
                zoneButton.setSheetY(64);
                zoneButton.setHoverX(224);
                zoneButton.setHoverY(64);
                zoneButton.setToolTip("gui.thermalharvest.zone.farmland");
                break;
            case ZoneRenderer.MODE_FULL:
                zoneButton.setSheetX(176);
                zoneButton.setSheetY(80);
                zoneButton.setHoverX(192);
                zoneButton.setHoverY(80);
                zoneButton.setToolTip("gui.thermalharvest.zone.full");
                break;
            default:
                zoneButton.setSheetX(176);
                zoneButton.setSheetY(64);
                zoneButton.setHoverX(192);
                zoneButton.setHoverY(64);
                zoneButton.setToolTip("gui.thermalharvest.zone.off");
                break;
        }
    }

    @Override
    public void handleElementButtonClick(String buttonName, int mouseButton) {
        if ("zone".equals(buttonName)) {
            ZoneRenderer.cycle(myTile);
            playClickSound(ZoneRenderer.getMode(myTile) == ZoneRenderer.MODE_OFF ? 0.7F : 1.0F);
            updateZoneButton();
        }
    }

    @Override
    protected void updateElementInformation() {
        super.updateElementInformation();
        // Значения — типы сторон CoFH: 1 ввод (синий), 4 вывод (оранжевый), 5 ввод осн. (зелёный),
        // 6 ввод доп. (фиолетовый), 8 всё сразу. Логика — как в GuiBrewer из Thermal Expansion.
        boolean output = baseTile.hasSideType(4) || baseTile.hasSideType(8);
        boolean inputAll = baseTile.hasSideType(1) || baseTile.hasSideType(8);
        for (ElementSlotOverlay overlay : outputOverlays) {
            overlay.setVisible(output);
        }
        ElementSlotOverlay.SlotRender half = inputAll ? ElementSlotOverlay.SlotRender.BOTTOM : ElementSlotOverlay.SlotRender.FULL;

        fertilizerOverlay.setVisible(inputAll);
        fertilizerOverlayPrimary.setVisible(baseTile.hasSideType(5));
        fertilizerOverlayPrimary.setSlotRender(half);

        boolean tank = myTile.hasIrrigation();
        tankBackground.setVisible(tank);
        tankElement.setVisible(tank);
        tankOverlay.setVisible(tank && inputAll);
        tankOverlaySecondary.setVisible(tank && baseTile.hasSideType(6));
        tankOverlaySecondary.setSlotRender(half);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int x, int y) {
        super.drawGuiContainerForegroundLayer(x, y);
        String area = areaText();
        int color = myTile.isIrrigating() ? 0x1F6FBF : 0x404040;
        fontRenderer.drawString(area, AREA_X - fontRenderer.getStringWidth(area) / 2, AREA_Y, color);
    }

    private String areaText() {
        int side = myTile.getAreaSide();
        return side + "x" + side;
    }

    @Override
    public void addTooltips(List<String> tooltip) {
        super.addTooltips(tooltip);
        String area = areaText();
        int w = fontRenderer.getStringWidth(area);
        int left = AREA_X - w / 2;
        if (mouseX >= left && mouseX < left + w && mouseY >= AREA_Y - 1 && mouseY < AREA_Y + 9) {
            tooltip.add(StringHelper.localize("gui.thermalharvest.area") + ": " + area + "x3");
            tooltip.add(StringHelper.localizeFormat("gui.thermalharvest.scan", myTile.getScanRate()));
            if (myTile.hasIrrigation()) {
                tooltip.add(StringHelper.localize(myTile.isIrrigating()
                        ? "gui.thermalharvest.irrigation.on" : "gui.thermalharvest.irrigation.off"));
            }
        }
    }
}
