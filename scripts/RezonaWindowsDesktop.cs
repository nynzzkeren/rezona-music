using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Text;
using System.Windows.Forms;

namespace RezonaDesktop
{
    static class Program
    {
        private const string LiveWebFallbackUrl = "https://rezona-music-web.vercel.app/desktop.html";

        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            // 1. Auto-start NUSA Dynamic Island for Windows (github.com/rama2555/nusa) if installed
            TryAutoStartNusaDynamicIsland();

            // 2. Materialize embedded desktop.html to %LOCALAPPDATA%\RezonaDesktop\desktop.html
            // so the Windows .EXE NEVER hits a 404 even when downloaded alone into Downloads!
            string targetUrl = ExtractEmbeddedDesktopAppUrl();

            // 3. Launch in Native Frameless App Window
            if (TryLaunchNativeAppWindow(targetUrl))
            {
                return;
            }

            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = targetUrl,
                    UseShellExecute = true
                });
            }
            catch (Exception ex)
            {
                MessageBox.Show("Gagal membuka Rezona Windows Studio: " + ex.Message, "Rezona Desktop", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private static string ExtractEmbeddedDesktopAppUrl()
        {
            try
            {
                // First check if local workspace web/public/desktop.html exists
                string workspaceHtml = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "web", "public", "desktop.html");
                if (File.Exists(workspaceHtml))
                {
                    return new Uri(workspaceHtml).AbsoluteUri;
                }

                // Otherwise extract embedded resource to %LOCALAPPDATA%\RezonaDesktop\desktop.html
                string appDir = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "RezonaDesktop"
                );
                Directory.CreateDirectory(appDir);

                Assembly asm = Assembly.GetExecutingAssembly();
                string htmlOut = Path.Combine(appDir, "desktop.html");
                using (Stream s = asm.GetManifestResourceStream("desktop.html"))
                {
                    if (s != null)
                    {
                        using (FileStream fs = new FileStream(htmlOut, FileMode.Create, FileAccess.Write))
                        {
                            s.CopyTo(fs);
                        }
                    }
                }

                string logoOut = Path.Combine(appDir, "logo.png");
                using (Stream ls = asm.GetManifestResourceStream("logo.png"))
                {
                    if (ls != null)
                    {
                        using (FileStream fs = new FileStream(logoOut, FileMode.Create, FileAccess.Write))
                        {
                            ls.CopyTo(fs);
                        }
                    }
                }

                if (File.Exists(htmlOut))
                {
                    // Fix relative /logo.png references for local file:// protocol
                    string htmlContent = File.ReadAllText(htmlOut, Encoding.UTF8);
                    htmlContent = htmlContent.Replace("src=\"/logo.png\"", "src=\"logo.png\"")
                                             .Replace("href=\"/logo.png\"", "href=\"logo.png\"")
                                             .Replace("href=\"/\"", "href=\"https://rezona-music-web.vercel.app/\"");
                    File.WriteAllText(htmlOut, htmlContent, Encoding.UTF8);
                    return new Uri(htmlOut).AbsoluteUri;
                }
            }
            catch
            {
            }

            return LiveWebFallbackUrl;
        }

        private static void TryAutoStartNusaDynamicIsland()
        {
            try
            {
                if (Process.GetProcessesByName("nusa").Length > 0)
                {
                    return;
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

        private static bool TryLaunchNativeAppWindow(string targetUrl)
        {
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
