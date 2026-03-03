@echo off
git branch -M main
git remote set-url origin https://github.com/FIRECATCHER404/Officerooms.git 2>nul
if errorlevel 1 git remote add origin https://github.com/FIRECATCHER404/Officerooms.git
git commit -m "Initial release" || echo No local changes to commit.
git fetch origin main
git pull --rebase origin main
git push -u origin main
