# Dyplink BOM

`dyplink-bom` aligns versions across Dyplink SDK modules.

## Usage

```kotlin
dependencies {
	implementation(platform("in.dyplink:dyplink-bom:0.1.0"))

	implementation("in.dyplink:dyplink-core")
	implementation("in.dyplink:dyplink-push")
	implementation("in.dyplink:dyplink-banners")
	implementation("in.dyplink:dyplink-messages")
}
```

Replace `0.1.0` with the version you want to consume.

