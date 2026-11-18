package net.amaguri.senjou.client.grime;

/**
 * 汚れブロックのクライアント側初期化。
 * 26.2 には BlockRenderLayerMap 相当の Fabric API が無く、チャンクの描画レイヤーはテクスチャの透明度から
 * 自動で決まる（ChunkSectionLayer.byTransparency）。grime_*.png は半透明ピクセルを含むので TRANSLUCENT になる。
 * そのためここで設定することは無い。
 */
public final class GrimeClient {
	private GrimeClient() {
	}

	public static void init() {
	}
}
