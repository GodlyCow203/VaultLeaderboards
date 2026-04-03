# How to Upload VaultLeaderboards to the Cloud

## Option 1: PlaceholderAPI eCloud (Recommended) ⭐

This is the official PlaceholderAPI expansion cloud where users can install your plugin automatically.

### Step 1: Prepare Your Project

Your plugin is already ready! Make sure:
- ✅ `plugin.yml` has correct metadata
- ✅ `pom.xml` version is updated
- ✅ JAR file is built (`mvn clean package`)

### Step 2: Create a eCloud Account

1. Go to https://ecloud.host
2. Click "Sign Up" 
3. Create an account with your GitHub username
4. Verify your email

### Step 3: Register Your Expansion

1. Login to eCloud
2. Click "Submit a New Expansion"
3. Fill in the form:
   - **Name:** VaultLeaderboards
   - **Author:** _GodlyCow
   - **GitHub Repository:** https://github.com/GodlyCow203/VaultLeaderboards
   - **Description:** Vault economy leaderboards with PlaceholderAPI support, FastStats integration
   - **Icon/Logo:** Upload a 256x256 PNG image

### Step 4: Link Your GitHub Release

1. Go to your GitHub repo
2. Create a release with the JAR file:
   ```
   gh release create v1.6 target/VaultLeaderboards-1.6.jar
   ```
   Or manually through GitHub:
   - Go to "Releases"
   - Click "Create a new release"
   - Tag: `v1.6`
   - Title: `VaultLeaderboards 1.6`
   - Upload your JAR file
   - Publish

3. The eCloud will automatically detect the release and make it available

### Step 5: Configure Auto-Updates

In your `plugin.yml`, the eCloud will use:
```yaml
name: VaultLeaderboards
version: 1.7
author: _GodlyCow
website: https://github.com/GodlyCow203/VaultLeaderboards
```

### How Users Install

Once approved, users can install via:
```
/papi ecloud download VaultLeaderboards
/papi reload
```

---

## Option 2: SpigotMC Resource Page

SpigotMC is another popular plugin distribution center.

### Steps:

1. Go to https://www.spigotmc.org
2. Create an account if you don't have one
3. Go to "Manage Resources" → "Create Resource"
4. Fill in:
   - **Name:** VaultLeaderboards
   - **Category:** Economy
   - **Description:** Use your README content
   - **Logo:** Upload an icon
   - **Support Link:** GitHub Issues URL
5. Upload your JAR to the resource

---

## Option 3: Paper Hangar

Paper's plugin repository with automatic updates.

### Steps:

1. Go to https://hangar.papermc.io
2. Create an account
3. Click "New Project"
4. Fill details and upload JAR
5. Create releases for updates

---

## Option 4: GitHub Releases (Free & Simple)

If you want users to download directly from GitHub:

### Create a Release:

```bash
# Navigate to your project
cd c:\Users\millm\Desktop\VaultLeaderboards

# Create a release (requires GitHub CLI)
gh release create v1.6 target/VaultLeaderboards-1.6.jar \
  --title "VaultLeaderboards 1.6" \
  --notes "Release notes here"
```

**Or manually:**
1. Go to https://github.com/GodlyCow203/VaultLeaderboards
2. Click "Releases" → "Create a new release"
3. Tag: `v1.6`
4. Release title: `VaultLeaderboards 1.6`
5. Description: Add changelog/features
6. Upload JAR file
7. Publish

---

## Recommended Upload Sequence

### For Maximum Visibility:

1. **Create GitHub Release** (easiest, free)
2. **Submit to eCloud** (most useful for PlaceholderAPI users)
3. **Upload to SpigotMC** (larger audience)
4. **Submit to Paper Hangar** (modern alternative)

---

## Version Updates

After each update:

1. Update version in `pom.xml`:
   ```xml
   <version>1.7</version>
   ```

2. Update `plugin.yml`:
   ```yaml
   version: 1.7
   ```

3. Build:
   ```bash
   & "D:\maven\apache-maven-3.9.12-bin\apache-maven-3.9.12\bin\mvn.cmd" clean package
   ```

4. Create a GitHub Release with the new JAR

5. The eCloud will automatically detect and update

---

## Quick Command Reference

### Build JAR:
```powershell
& "D:\maven\apache-maven-3.9.12-bin\apache-maven-3.9.12\bin\mvn.cmd" clean package
```

### Create GitHub Release (CLI):
```bash
gh release create v1.7 target/VaultLeaderboards-1.7.jar
```

### View Release on GitHub:
```
https://github.com/GodlyCow203/VaultLeaderboards/releases
```

---

## Troubleshooting

**eCloud won't accept my repository:**
- Make sure you're logged in with the same GitHub account
- Ensure your pom.xml version matches the tag
- Check that plugin.yml is correctly formatted

**JAR file too large:**
- Try removing debug symbols: Add `-DskipTests` to Maven build
- Use maven-shade-plugin (already configured)

**Users can't download:**
- Check release is public on GitHub
- Verify JAR filename is correct
- Test download link manually

---

**Next Steps:**
1. Update version to 1.7 if needed
2. Build the project
3. Create a GitHub release
4. Submit to eCloud
5. Monitor for user feedback and issues

Enjoy sharing your plugin! 🎉
