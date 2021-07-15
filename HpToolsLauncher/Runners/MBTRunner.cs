using QTObjectModelLib;
using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

namespace HpToolsLauncher
{
    public class MBTRunner : RunnerBase, IDisposable
    {
        private readonly object _lockObject = new object();
        private string parentFolder;//folder in which we will create new tests
        private string repoFolder;
        private IEnumerable<MBTTest> tests;

        public MBTRunner(string parentFolder, string repoFolder, IEnumerable<MBTTest> tests)
        {
            this.parentFolder = parentFolder;
            this.repoFolder = repoFolder;
            this.tests = tests;
        }

        public override TestSuiteRunResults Run()
        {
            var type = Type.GetTypeFromProgID("Quicktest.Application");

            lock (_lockObject)
            {
                Application _qtpApplication = Activator.CreateInstance(type) as Application;
                try
                {
                    if (Directory.Exists(parentFolder))
                    {
                        Directory.Delete(parentFolder, true);
                    }
                    ConsoleWriter.WriteLine("Using parent folder : " + parentFolder);
                }
                catch (Exception e)
                {
                    ConsoleWriter.WriteErrLine("Failed to delete parent folder : " + e.Message);
                }

                Directory.CreateDirectory(parentFolder);
                DirectoryInfo parentDir = new DirectoryInfo(parentFolder);

                try
                {
                    if (_qtpApplication.Launched)
                    {
                        _qtpApplication.Quit();
                    }
                }
                catch (Exception e)
                {
                    ConsoleWriter.WriteErrLine("Failed to close qtpApp : " + e.Message);
                }



                //START Test creation
                //_qtpApplication.Launch();
                //_qtpApplication.Visible = false;
                foreach (var test in tests)
                {
                    DateTime startTotal = DateTime.Now;
                    ConsoleWriter.WriteLine("Creation of " + test.Name + " *****************************");
                    LoadNeededAddins(_qtpApplication, test.UnderlyingTests);
                    try
                    {
                        DateTime startSub1 = DateTime.Now;
                        _qtpApplication.New();
                        ConsoleWriter.WriteLine(string.Format("_qtpApplication.New took {0:0.0} secs", DateTime.Now.Subtract(startSub1).TotalSeconds));
                        QTObjectModelLib.Action qtAction1 = _qtpApplication.Test.Actions[1];
                        qtAction1.Description = "unitIds=" + string.Join(",", test.UnitIds);

                        //https://myskillpoint.com/how-to-use-loadandrunaction-in-uft/#LoadAndRunAction_Having_Input-Output_Parameters
                        //LoadAndRunAction "E:\UFT_WorkSpace\TestScripts\SampleTest","Action1",0,"inputParam1","inputParam2",outParameterVal
                        //string actionContent = "LoadAndRunAction \"c:\\Temp\\GUITest2\\\",\"Action1\"";
                        string actionContent = File.Exists(test.Script) ? File.ReadAllText(test.Script) : test.Script;
                        qtAction1.ValidateScript(actionContent);
                        qtAction1.SetScript(actionContent);

                        DirectoryInfo fullDir = parentDir;
                        if (!string.IsNullOrEmpty(test.PackageName))
                        {
                            fullDir = fullDir.CreateSubdirectory(test.PackageName);
                        }

                        //add function library
                        foreach (string fl in test.FunctionLibraries)
                        {
                            string fileName = GetResourceFileNameAndAddToUftFoldersIfRequired(_qtpApplication, fl);
                            _qtpApplication.Test.Settings.Resources.Libraries.Add(fileName);
                        }

                        //add recovery scenario
                        foreach (RecoveryScenario rs in test.RecoveryScenarios)
                        {
                            string fileName = GetResourceFileNameAndAddToUftFoldersIfRequired(_qtpApplication, rs.FileName);
                            _qtpApplication.Test.Settings.Recovery.Add(fileName, rs.Name, rs.Position);
                        }

                        //Expects to receive params in CSV format, encoded base64
                        if (!string.IsNullOrEmpty(test.DatableParams))
                        {
                            string tempCsvFileName = Path.Combine(parentFolder, "temp.csv");
                            if (File.Exists(tempCsvFileName))
                            {
                                File.Delete(tempCsvFileName);
                            }

                            byte[] data = Convert.FromBase64String(test.DatableParams);
                            string decodedParams = Encoding.UTF8.GetString(data);

                            File.WriteAllText(tempCsvFileName, decodedParams);
                            _qtpApplication.Test.DataTable.Import(tempCsvFileName);
                            File.Delete(tempCsvFileName);
                        }

                        string fullPath = fullDir.CreateSubdirectory(test.Name).FullName;
                        _qtpApplication.Test.SaveAs(fullPath);
                        double sec = DateTime.Now.Subtract(startTotal).TotalSeconds;
                        ConsoleWriter.WriteLine(string.Format("MBT test was created in {0} in {1:0.0} secs", fullPath, sec));

                    }
                    catch (Exception e)
                    {
                        ConsoleWriter.WriteErrLine("Fail in MBTRunner : " + e.Message);
                    }
                }
                if (_qtpApplication.Launched)
                {
                    _qtpApplication.Quit();
                }
            }

            return null;
        }

        private string GetResourceFileNameAndAddToUftFoldersIfRequired(Application qtpApplication, string filePath)
        {
            //file path might be full or just file name;
            string location = qtpApplication.Folders.Locate(filePath);
            if (!string.IsNullOrEmpty(location))
            {
                ConsoleWriter.WriteLine(string.Format("Adding resources : {0} - done", filePath));
            }
            else
            {
                ConsoleWriter.WriteLine(string.Format("Adding resources : {0} - failed to find file in repository. Please check correctness of resource location.", filePath));
            }
            /*else
            {
                string[] allFiles = Directory.GetFiles(repoFolder, fileName, SearchOption.AllDirectories);
                if (allFiles.Length == 0)
                {
                    ConsoleWriter.WriteLine(string.Format("Adding resources : {0} - failed to find file in repository. Please check correctness of resource name.", fileName));
                }
                else if (allFiles.Length > 1)
                {
                    //we found several possible locations
                    //if resource has full path, we can try to find it in found paths 
                    //for example resource : c://aa/bb/repo/resourceName
                    //one of found paths is : c:/jenkins/repo/resourceName , after removing repo is will be /repo/resourceName
                    //so /repo/resourceName is last part of c://aa/bb/repo/resourceName
                    bool found = false;
                    if (Path.IsPathRooted(filePath))
                    {
                        foreach (string path in allFiles)
                        {
                            string pathInRepo = path.Replace(repoFolder,"");
                            if (filePath.EndsWith(pathInRepo))
                            {
                                string directoryPath = new FileInfo(path).Directory.FullName;
                                ConsoleWriter.WriteLine(string.Format("Adding resources : {0} - folder {1} is added to settings", fileName, directoryPath.Replace(repoFolder, "")));
                                qtpApplication.Folders.Add(directoryPath);
                                found = true;
                                break;
                            }
                        }
                    }

                    if (!found)
                    {
                        StringBuilder sb = new StringBuilder();
                        foreach (string path in allFiles)
                        {
                            string directoryPath = new FileInfo(path).Directory.FullName;
                            sb.Append(directoryPath).Append("; ");
                        }
                        ConsoleWriter.WriteLine(string.Format("Adding resources : {0} - found more than 1 file in repo. Please define 'Folder location' manually in (Tools->Options->GUI Testing->Folders). Possible values : {1}", fileName, sb.ToString()));
                    }
                }
                else//found ==1
                {
                    string directoryPath = new FileInfo(allFiles[0]).Directory.FullName;
                    ConsoleWriter.WriteLine(string.Format("Adding resources : {0} - folder {1} is added to settings", fileName, directoryPath.Replace(repoFolder,"")));
                    qtpApplication.Folders.Add(directoryPath);
                }
            }*/

            return filePath;
        }

        private void LoadNeededAddins(Application _qtpApplication, IEnumerable<String> fileNames)
        {
            try
            {
                HashSet<string> addinsSet = new HashSet<string>();
                foreach (string fileName in fileNames)
                {
                    try
                    {
                        DateTime start1 = DateTime.Now;
                        var testAddinsObj = _qtpApplication.GetAssociatedAddinsForTest(fileName);
                        ConsoleWriter.WriteLine(string.Format("GetAssociatedAddinsForTest took {0:0.0} secs", DateTime.Now.Subtract(start1).TotalSeconds));
                        object[] tempTestAddins = (object[])testAddinsObj;

                        foreach (string addin in tempTestAddins)
                        {
                            addinsSet.Add(addin);
                        }
                    }
                    catch (Exception testErr)
                    {
                        ConsoleWriter.WriteErrLine("Fail to LoadNeededAddins for : " + fileName + ", " + testErr.Message);
                    }
                }

                //if (_qtpApplication.Launched)
                //{
                //_qtpApplication.Quit();
                //ConsoleWriter.WriteLine("LoadNeededAddins : _qtpApplication.Quit");
                //}

                object erroDescription = null;

                string[] addinsArr = new string[addinsSet.Count];
                addinsSet.CopyTo(addinsArr);
                ConsoleWriter.WriteLine("Loading Addins : " + string.Join(",", addinsArr));
                DateTime start2 = DateTime.Now;
                _qtpApplication.SetActiveAddins(addinsArr, out erroDescription);
                ConsoleWriter.WriteLine(string.Format("SetActiveAddins took {0:0.0} secs", DateTime.Now.Subtract(start2).TotalSeconds));
                if (!string.IsNullOrEmpty((string)erroDescription))
                {
                    ConsoleWriter.WriteErrLine("Fail to SetActiveAddins : " + erroDescription);
                }
            }
            catch (Exception globalErr)
            {
                ConsoleWriter.WriteErrLine("Fail to LoadNeededAddins : " + globalErr.Message);
                // Try anyway to run the test
            }
        }
    }

    public class RecoveryScenario
    {
        public string FileName { get; set; }
        public string Name { get; set; }
        public int Position { get; set; }

        public static RecoveryScenario ParseFromString(string content)
        {
            RecoveryScenario rs = new RecoveryScenario();
            string[] parts = content.Split(',');//expected 3 parts separated by , : location,name,position(default is -1)
            if (parts.Length < 2)
            {
                ConsoleWriter.WriteErrLine("Fail to parse recovery scenario (need at least 2 parts, separated with ,): " + content);
                return null;
            }
            rs.FileName = parts[0];
            rs.Name = parts[1];
            if (parts.Length >= 3)
            {
                try
                {
                    rs.Position = int.Parse(parts[2]);
                }
                catch (Exception e)
                {
                    ConsoleWriter.WriteErrLine("Fail to parse position of recovery scenario : " + content + " : " + e.Message);
                    rs.Position = -1;
                }
            }
            else
            {
                rs.Position = -1;
            }

            return rs;
        }
    }

    public class MBTTest
    {
        public string Name { get; set; }
        public string Script { get; set; }
        public string UnitIds { get; set; }
        public List<string> UnderlyingTests { get; set; }
        public string PackageName { get; set; }
        public string DatableParams { get; set; }

        public List<string> FunctionLibraries { get; set; }

        public List<RecoveryScenario> RecoveryScenarios { get; set; }
    }


}
