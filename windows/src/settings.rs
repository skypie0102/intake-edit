use std::env;
use std::fs::{self, File};
use std::io::Write;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use keyring::{Entry, Error as KeyringError};

use crate::model::RepoSettings;

const KEYRING_SERVICE: &str = "cloud.shadowmonarchbooks.intake-edit";
const KEYRING_USERNAME: &str = "github-token";

pub struct SettingsStore { path: PathBuf }
impl SettingsStore {
    pub fn new_default() -> Result<Self> {
        let base = env::var_os("LOCALAPPDATA").map(PathBuf::from).or_else(|| env::current_dir().ok()).context("could not determine local application-data directory")?;
        Ok(Self::new(base.join("ShadowMonarchBooks").join("IntakeEdit")))
    }
    pub fn new(root: PathBuf) -> Self { Self { path: root.join("settings.json") } }
    pub fn load(&self) -> Result<RepoSettings> {
        if !self.path.exists() { return Ok(RepoSettings::default()); }
        let raw = fs::read_to_string(&self.path).with_context(|| format!("could not read {}", self.path.display()))?;
        serde_json::from_str(&raw).with_context(|| format!("could not parse {}", self.path.display()))
    }
    pub fn save(&self, settings: &RepoSettings) -> Result<()> {
        let parent = self.path.parent().context("settings path has no parent directory")?; fs::create_dir_all(parent)?; let temp = self.path.with_extension("tmp"); let payload = serde_json::to_vec_pretty(settings)?;
        { let mut file = File::create(&temp)?; file.write_all(&payload)?; file.write_all(b"\n")?; file.sync_all()?; }
        replace_file(&temp, &self.path)?; Ok(())
    }
    pub fn path(&self) -> &Path { &self.path }
}

pub struct TokenStore { entry: Entry }
impl TokenStore {
    pub fn new() -> Result<Self> { let entry = Entry::new(KEYRING_SERVICE, KEYRING_USERNAME).context("could not initialize Windows Credential Manager")?; Ok(Self { entry }) }
    pub fn load(&self) -> Result<Option<String>> { match self.entry.get_password() { Ok(token) => Ok(Some(token)), Err(KeyringError::NoEntry) => Ok(None), Err(error) => Err(error).context("could not read GitHub token from Windows Credential Manager") } }
    pub fn save(&self, token: &str) -> Result<()> { self.entry.set_password(token).context("could not save GitHub token to Windows Credential Manager") }
    #[allow(dead_code)]
    pub fn delete(&self) -> Result<()> { match self.entry.delete_credential() { Ok(()) | Err(KeyringError::NoEntry) => Ok(()), Err(error) => Err(error).context("could not delete GitHub token from Windows Credential Manager") } }
}
fn replace_file(temp: &Path, target: &Path) -> Result<()> { if target.exists() { fs::remove_file(target)?; } fs::rename(temp, target)?; Ok(()) }
