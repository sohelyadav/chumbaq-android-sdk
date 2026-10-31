# Chumbaq BOM

`chumbaq-bom` aligns versions across Chumbaq SDK modules.

## Usage

```kotlin
dependencies {
	implementation(platform("in.chumbaq:chumbaq-bom:0.1.0"))

	implementation("in.chumbaq:chumbaq-core")
	implementation("in.chumbaq:chumbaq-push")
	implementation("in.chumbaq:chumbaq-banners")
	implementation("in.chumbaq:chumbaq-messages")
}
```

Replace `0.1.0` with the version you want to consume.

