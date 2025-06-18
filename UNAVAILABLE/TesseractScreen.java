package cat.lacycat.tesseracts;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class TesseractScreen extends HandledScreen<TesseractScreenHandler> {
    private static final Identifier TEXTURE = new Identifier("tesseracts", "textures/gui/tesseract.png");
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 222;

    private TextFieldWidget networkNameField;
    private TextFieldWidget tesseractNameField;
    private ButtonWidget connectButton;
    private ButtonWidget modeButton;
    private ButtonWidget refreshButton;

    private int scrollOffset = 0;
    private final List<String> availableNetworks = new ArrayList<>();
    private boolean needsRefresh = true;

    public TesseractScreen(TesseractScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = GUI_WIDTH;
        this.backgroundHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // 네트워크 이름 입력 필드
        networkNameField = new TextFieldWidget(this.textRenderer, x + 8, y + 20, 100, 12, Text.literal(""));
        networkNameField.setMaxLength(32);
        networkNameField.setText(getNetworkName());
        addDrawableChild(networkNameField);

        // 테서랙트 이름 입력 필드
        tesseractNameField = new TextFieldWidget(this.textRenderer, x + 8, y + 40, 100, 12, Text.literal(""));
        tesseractNameField.setMaxLength(32);
        tesseractNameField.setText(getTesseractName());
        addDrawableChild(tesseractNameField);

        // 연결 버튼
        connectButton = ButtonWidget.builder(Text.literal("Connect"), button -> {
            // 네트워크 연결 패킷 전송
            String networkName = networkNameField.getText().trim();
            String tesseractName = tesseractNameField.getText().trim();
            if (!networkName.isEmpty()) {
                // TODO: 서버에 패킷 전송
                handler.setNetworkName(networkName);
                handler.setTesseractName(tesseractName);
            }
        }).dimensions(x + 112, y + 20, 56, 16).build();
        addDrawableChild(connectButton);

        // 모드 전환 버튼
        modeButton = ButtonWidget.builder(getModeText(), button -> {
            handler.cycleTesseractMode();
            button.setMessage(getModeText());
        }).dimensions(x + 112, y + 40, 56, 16).build();
        addDrawableChild(modeButton);

        // 새로고침 버튼
        refreshButton = ButtonWidget.builder(Text.literal("Refresh"), button -> {
            needsRefresh = true;
        }).dimensions(x + 112, y + 60, 56, 16).build();
        addDrawableChild(refreshButton);

        refreshNetworkList();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (needsRefresh) {
            refreshNetworkList();
            needsRefresh = false;
        }

        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight);

        // 에너지 바 그리기
        drawEnergyBar(context, x, y);

        // 네트워크 정보 그리기
        drawNetworkInfo(context, x, y);

        // 사용 가능한 네트워크 목록 그리기
        drawNetworkList(context, x, y, mouseX, mouseY);
    }

    private void drawEnergyBar(DrawContext context, int x, int y) {
        TesseractBlockEntity tesseract = handler.getTesseract();
        if (tesseract == null) return;

        int barX = x + 8;
        int barY = y + 80;
        int barWidth = 160;
        int barHeight = 10;

        // 배경
        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);

        // 에너지 바
        float energyPercentage = tesseract.getEnergyPercentage();
        int fillWidth = (int) (barWidth * energyPercentage);

        int color = getEnergyBarColor(energyPercentage);
        context.fill(barX + 1, barY + 1, barX + fillWidth - 1, barY + barHeight - 1, color);

        // 에너지 텍스트
        String energyText = String.format("%d / %d FE (%.1f%%)",
                tesseract.getStoredEnergy(), tesseract.getMaxEnergy(), energyPercentage * 100);
        context.drawTextWithShadow(textRenderer, energyText, barX, barY - 10, 0xFFFFFF);
    }

    private int getEnergyBarColor(float percentage) {
        if (percentage > 0.75f) return 0xFF00FF00; // 녹색
        if (percentage > 0.5f) return 0xFFFFFF00;  // 노란색
        if (percentage > 0.25f) return 0xFFFF8800; // 주황색
        return 0xFFFF0000; // 빨간색
    }

    private void drawNetworkInfo(DrawContext context, int x, int y) {
        TesseractBlockEntity tesseract = handler.getTesseract();
        if (tesseract == null) return;

        TesseractNetwork network = TesseractNetworkManager.getNetwork(tesseract.getNetworkId());
        if (network == null) return;

        int infoY = y + 100;

        // 네트워크 정보
        context.drawTextWithShadow(textRenderer,
                Text.literal("Network: " + network.getNetworkName()), x + 8, infoY, 0xFFFFFF);
        infoY += 12;

        context.drawTextWithShadow(textRenderer,
                Text.literal("Tesseracts: " + network.getTesseractCount()), x + 8, infoY, 0xAAAAAAA);
        infoY += 12;

        long totalEnergy = network.getTotalStoredEnergy();
        long maxEnergy = network.getTotalMaxEnergy();
        context.drawTextWithShadow(textRenderer,
                Text.literal(String.format("Network Energy: %d / %d FE", totalEnergy, maxEnergy)),
                x + 8, infoY, 0xAAAAAAA);
    }

    private void drawNetworkList(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int listX = x + 8;
        int listY = y + 140;
        int listWidth = 160;
        int listHeight = 60;

        // 배경
        context.fill(listX, listY, listX + listWidth, listY + listHeight, 0xFF222222);
        context.drawBorder(listX, listY, listWidth, listHeight, 0xFF666666);

        // 제목
        context.drawTextWithShadow(textRenderer, "Available Networks:", listX + 4, listY - 10, 0xFFFFFF);

        // 네트워크 목록
        int itemHeight = 12;
        int maxVisible = listHeight / itemHeight;
        int startIndex = scrollOffset;
        int endIndex = Math.min(startIndex + maxVisible, availableNetworks.size());

        for (int i = startIndex; i < endIndex; i++) {
            String networkName = availableNetworks.get(i);
            int itemY = listY + (i - startIndex) * itemHeight + 2;

            // 마우스 오버 효과
            boolean isHovered = mouseX >= listX && mouseX <= listX + listWidth &&
                    mouseY >= itemY && mouseY <= itemY + itemHeight;

            if (isHovered) {
                context.fill(listX + 1, itemY, listX + listWidth - 1, itemY + itemHeight, 0xFF444444);
            }

            // 현재 연결된 네트워크 표시
            TesseractBlockEntity tesseract = handler.getTesseract();
            boolean isCurrentNetwork = tesseract != null &&
                    TesseractNetworkManager.getNetwork(tesseract.getNetworkId()) != null &&
                    TesseractNetworkManager.getNetwork(tesseract.getNetworkId()).getNetworkName().equals(networkName);

            int textColor = isCurrentNetwork ? 0xFF00FF00 : 0xFFFFFF;
            context.drawText(textRenderer, networkName, listX + 4, itemY + 2, textColor, false);

            // 네트워크 정보 (테서랙트 수)
            TesseractNetwork network = TesseractNetworkManager.getNetworkByName(networkName);
            if (network != null) {
                String info = "(" + network.getTesseractCount() + ")";
                int infoWidth = textRenderer.getWidth(info);
                context.drawText(textRenderer, info, listX + listWidth - infoWidth - 4, itemY + 2, 0xAAAAA, false);
            }
        }

        // 스크롤바 (필요한 경우)
        if (availableNetworks.size() > maxVisible) {
            drawScrollbar(context, listX + listWidth - 6, listY, 6, listHeight);
        }
    }

    private void drawScrollbar(DrawContext context, int x, int y, int width, int height) {
        // 스크롤바 배경
        context.fill(x, y, x + width, y + height, 0xFF333333);

        // 스크롤바 핸들
        int totalItems = availableNetworks.size();
        int visibleItems = height / 12;

        if (totalItems > visibleItems) {
            int handleHeight = Math.max(10, height * visibleItems / totalItems);
            int handleY = y + (scrollOffset * (height - handleHeight)) / (totalItems - visibleItems);

            context.fill(x + 1, handleY, x + width - 1, handleY + handleHeight, 0xFF666666);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int listY = (height - backgroundHeight) / 2 + 140;
        int listHeight = 60;

        if (mouseY >= listY && mouseY <= listY + listHeight) {
            int maxVisible = listHeight / 12;
            int maxScroll = Math.max(0, availableNetworks.size() - maxVisible);

            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) amount));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 네트워크 목록 클릭 처리
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        int listX = x + 8;
        int listY = y + 140;
        int listWidth = 160;
        int listHeight = 60;

        if (mouseX >= listX && mouseX <= listX + listWidth &&
                mouseY >= listY && mouseY <= listY + listHeight) {

            int itemHeight = 12;
            int clickedIndex = scrollOffset + (int) (mouseY - listY) / itemHeight;

            if (clickedIndex < availableNetworks.size()) {
                String selectedNetwork = availableNetworks.get(clickedIndex);
                networkNameField.setText(selectedNetwork);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void refreshNetworkList() {
        availableNetworks.clear();
        availableNetworks.addAll(TesseractNetworkManager.getAllNetworkNames());
        availableNetworks.sort(String::compareToIgnoreCase);
    }

    private String getNetworkName() {
        TesseractBlockEntity tesseract = handler.getTesseract();
        if (tesseract != null) {
            TesseractNetwork network = TesseractNetworkManager.getNetwork(tesseract.getNetworkId());
            return network != null ? network.getNetworkName() : "";
        }
        return "";
    }

    private String getTesseractName() {
        TesseractBlockEntity tesseract = handler.getTesseract();
        return tesseract != null ? tesseract.getTesseractName() : "";
    }

    private Text getModeText() {
        TesseractBlockEntity tesseract = handler.getTesseract();
        if (tesseract != null) {
            return switch (tesseract.getMode()) {
                case SEND_ONLY -> Text.literal("Send Only");
                case RECEIVE_ONLY -> Text.literal("Receive Only");
                case SEND_RECEIVE -> Text.literal("Send & Receive");
            };
        }
        return Text.literal("Unknown");
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // 제목 그리기
        context.drawText(this.textRenderer, this.title, 8, 6, 4210752, false);
        context.drawText(this.textRenderer, this.playerInventoryTitle, 8, this.backgroundHeight - 96 + 2, 4210752, false);

        // 라벨들 그리기
        context.drawText(this.textRenderer, "Network:", 8, 12, 4210752, false);
        context.drawText(this.textRenderer, "Name:", 8, 32, 4210752, false);
    }
}
