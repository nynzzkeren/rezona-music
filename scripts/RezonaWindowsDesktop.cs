using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

namespace RezonaDesktop
{
    static class Program
    {
        private const string DesktopUrl = "https://rezona-music.vercel.app/desktop.html";

        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            // Auto-start NUSA Dynamic Island for Windows (github.com/rama2555/nusa) if installed on this PC
            TryAutoStartNusaDynamicIsland();

            // Launch Rezona Windows Studio in native App Window mode
            if (TryLaunchNativeAppWindow())
            {
                return;
            }

            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = DesktopUrl,
                    UseShellExecute = true
                });
            }
            catch (Exception ex)
            {
                MessageBox.Show("Gagal membuka Rezona Windows Studio: " + ex.Message, "Rezona Desktop", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private static void TryAutoStartNusaDynamicIsland()
        {
            try
            {
                if (Process.GetProcessesByName("nusa").Length > 0)
                {
                    return; // Already running
                }

                string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                string progFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
                string baseDir = AppDomain.CurrentDomain.BaseDirectory;

                string[] nusaCandidates = new string[]
                {
                    Path.Combine(baseDir, "nusa.exe"),
                    Path.Combine(localAppData, @"Programs\nusa\nusa.exe"),
                    Path.Combine(localAppData, @"Nusa\nusa.exe"),
                    Path.Combine(progFiles, @"Nusa\nusa.exe")
                };

                foreach (string candidate in nusaCandidates)
                {
                    if (File.Exists(candidate))
                    {
                        Process.Start(new ProcessStartInfo
                        {
                            FileName = candidate,
                            UseShellExecute = true
                        });
                        break;
                    }
                }
            }
            catch
            {
            }
        }

        private static bool TryLaunchNativeAppWindow()
        {
            string localHtml = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "web", "public", "desktop.html");
            string targetUrl = File.Exists(localHtml) ? new Uri(localHtml).AbsoluteUri : DesktopUrl;

            string[] browserCandidates = new string[]
            {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), @"Microsoft\Edge\Application\msedge.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), @"Microsoft\Edge\Application\msedge.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), @"Google\Chrome\Application\chrome.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), @"Google\Chrome\Application\chrome.exe")
            };

            foreach (string exe in browserCandidates)
            {
                if (File.Exists(exe))
                {
                    try
                    {
                        Process.Start(new ProcessStartInfo
                        {
                            FileName = exe,
                            Arguments = "--app=\"" + targetUrl + "\" --window-size=1360,860",
                            UseShellExecute = false
                        });
                        return true;
                    }
                    catch
                    {
                    }
                }
            }
            return false;
        }
    }
}
