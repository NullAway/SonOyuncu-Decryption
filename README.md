# SonOyuncu-Decryption

Decryptor & string deobfuscator for the Sonoyuncu Minecraft client's `game.jar`.

Takes the encrypted `game.jar` from `%APPDATA%\.sonoyuncu`, peels off its protection layers and produces a fully readable jar — strings deobfuscated, dead decryptor code removed.

## What it does

1. **Layer 1** — AES-256-CBC decryption using the embedded round-key schedule
2. **Layer 2** — custom T-table based stream-mixer payload decryption
3. **Validation** — every class is parsed and checked (magic, version, constant pool, this-class name)
4. **String deobfuscation** — auto-detects the obfuscator's string pattern in an isolated worker JVM:
   - static-initializes every class (with the client's own library jars on the classpath)
   - retries broken classes through a `<clinit>` harness so their decryptors still run
   - inlines resolved strings straight into the bytecode (`ldc "real string"`)
5. **Dead-code cleanup** — removes leftover string-cache fields, decryptor `<clinit>` blocks and junk instructions

Nothing is hardcoded to a specific build: the string transformer auto-detects the pattern, so it keeps working as `game.jar` changes between launcher updates.

## Requirements

- **Java (JDK) 22** — both for building and running (older JDKs work at runtime, but the build script expects 22)
- Windows (output path resolution uses `%APPDATA%`)
- `libs/asm.jar` and `libs/asm-tree.jar` (bundled) for building

## Usage

Just run the jar:

```
java -jar So-Decrypt.jar
```

It grabs `%APPDATA%\.sonoyuncu\game.jar` and writes `game-decrypted-deluxe1447.jar` next to `So-Decrypt.jar`.

Optional overrides:

```
java -jar So-Decrypt.jar <input.jar> <output.jar>
```

## Building

```
build.bat
```

Compiles `src/`, bundles ASM into the jar and produces `So-Decrypt.jar`.

## Project layout

```
src/main/java/com/deluxe/sonoyuncu/
├── Main.java              entry point, banner, argument handling
├── decrypt/               protection-layer removers
│   ├── AesDecryptor.java      layer 1: AES-256-CBC
│   ├── TTableBuilder.java     stream-mixer T table
│   ├── ClassDecryptor.java    layer 2: per-byte stream decrypt
│   └── Decryptor.java         jar pipeline + validation
├── transform/             string deobfuscation
│   ├── HarvestRunner.java     isolated worker JVM, runtime string harvest
│   ├── StringTransformer.java ASM rewrite, inlines resolved strings
│   ├── StringDeobfuscator.java orchestrates harvest -> rewrite -> cleanup
│   └── DeadCodeRemover.java   dead fields / decryptor clinit / junk removal
└── utils/
    ├── Banner.java            ascii art banner
    ├── ConsoleColors.java     gradient output + progress
    └── ClassFileUtil.java     class-file parsing / validation
```

## Example output

```
[+] DONE: 4103/4103 classes decrypted+validated in 2.0s (other files: 12)
[+] String deobfuscation: 894 strings deobfuscated (613 unique) across 201 classes
[+] Dead-code cleanup: 119 dead string fields removed (118 classes)
[+] OUTPUT: ...\game-decrypted-deluxe1447.jar
```

## Note

> [!WARNING]
> This project is for **educational purposes only**. It is intended to demonstrate how client-side protection layers and string obfuscation work in a controlled, local environment.
>
> Sonoyuncu's terms of service may prohibit modifying, decrypting or reverse-engineering the client. Using this tool against the live client may violate those terms and can get your account banned. You are fully responsible for how you use this code. The authors accept no liability for any misuse or consequences.
