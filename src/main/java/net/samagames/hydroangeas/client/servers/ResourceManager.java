package net.samagames.hydroangeas.client.servers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.samagames.hydroangeas.client.HydroangeasClient;
import net.samagames.hydroangeas.common.protocol.MinecraftServerIssuePacket;
import net.samagames.hydroangeas.utils.InternetUtils;

import java.io.*;
import java.util.HashMap;

public class ResourceManager
{
    private final HydroangeasClient instance;

    public ResourceManager(HydroangeasClient instance)
    {
        this.instance = instance;
    }

    public void downloadServer(MinecraftServerC server, File serverPath)
    {
        try
        {
            String existURL = this.instance.getTemplatesDomain() + "servers/exist.php?game=" + server.getGame();
            String wgetURL = this.instance.getTemplatesDomain() + "servers/" + server.getGame() + ".tar.gz";
            boolean exist = Boolean.valueOf(InternetUtils.readURL(existURL));

            if (!exist)
            {
                instance.getConnectionManager().sendPacket(new MinecraftServerIssuePacket(this.instance.getClientUUID(), server.getServerName(), MinecraftServerIssuePacket.Type.MAKE));
                throw new IllegalStateException("Server template don't exist!");
            }

            this.instance.getLinuxBridge().wget(wgetURL, serverPath.getAbsolutePath());
            this.instance.getLinuxBridge().gzipExtract(new File(serverPath, server.getGame() + ".tar.gz").getAbsolutePath(), serverPath.getAbsolutePath());
        }
        catch (Exception e)
        {
            e.printStackTrace();
            instance.getConnectionManager().sendPacket(new MinecraftServerIssuePacket(this.instance.getClientUUID(), server.getServerName(), MinecraftServerIssuePacket.Type.MAKE));
        }
    }

    public void downloadMap(MinecraftServerC server, File serverPath)
    {
        try
        {
            String existURL = this.instance.getTemplatesDomain() + "maps/exist.php?game=" + server.getGame() + "&map=" + server.getMap();
            String wgetURL = this.instance.getTemplatesDomain() + "maps/" + server.getGame() + "_" + server.getMap() + ".tar.gz";
            boolean exist = Boolean.valueOf(InternetUtils.readURL(existURL));

            if (!exist)
            {
                new MinecraftServerIssuePacket(this.instance.getClientUUID(), server.getServerName(), MinecraftServerIssuePacket.Type.MAKE);
                throw new IllegalStateException("Server's map don't exist!");
            }

            this.instance.getLinuxBridge().wget(wgetURL, serverPath.getAbsolutePath());
            this.instance.getLinuxBridge().gzipExtract(new File(serverPath, server.getGame() + "_" + server.getMap() + ".tar.gz").getAbsolutePath(), serverPath.getAbsolutePath());

        }
        catch (Exception e)
        {
            e.printStackTrace();
            instance.getConnectionManager().sendPacket(new MinecraftServerIssuePacket(this.instance.getClientUUID(), server.getServerName(), MinecraftServerIssuePacket.Type.MAKE));
        }
    }

    public void downloadDependencies(MinecraftServerC server, File serverPath)
    {
        try
        {
            File dependenciesFile = new File(serverPath, "dependencies.json");

            while(!dependenciesFile.exists()) {}

            JsonArray jsonRoot = new JsonParser().parse(new FileReader(dependenciesFile)).getAsJsonArray();

            for(int i = 0; i < jsonRoot.size(); i++)
            {
                JsonObject jsonDependency = jsonRoot.get(i).getAsJsonObject();
                this.downloadDependency(server, new ServerDependency(jsonDependency.get("name").getAsString(), jsonDependency.get("version").getAsString()), serverPath);
            }
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
    }

    public void downloadDependency(MinecraftServerC server, ServerDependency dependency, File serverPath)
    {
        try
        {
            String existURL = this.instance.getTemplatesDomain() + "dependencies/exist.php?name=" + dependency.getName() + "&version=" + dependency.getVersion();
            String wgetURL = this.instance.getTemplatesDomain() + "dependencies/" + dependency.getName() + "_" + dependency.getVersion() + ".tar.gz";
            File pluginsPath = new File(serverPath, "plugins/");

            if(!pluginsPath.exists())
                pluginsPath.mkdirs();

            boolean exist = Boolean.valueOf(InternetUtils.readURL(existURL));

            if (!exist)
            {
                instance.getConnectionManager().sendPacket(new MinecraftServerIssuePacket(this.instance.getClientUUID(), server.getServerName(), MinecraftServerIssuePacket.Type.MAKE));
                throw new IllegalStateException("Servers' dependency '" + dependency.getName() + "' don't exist!");
            }

            this.instance.getLinuxBridge().wget(wgetURL, pluginsPath.getAbsolutePath());
            this.instance.getLinuxBridge().gzipExtract(new File(pluginsPath, dependency.getName() + "_" + dependency.getVersion() + ".tar.gz").getAbsolutePath(), pluginsPath.getAbsolutePath());
        }
        catch (Exception e)
        {
            e.printStackTrace();
            instance.getConnectionManager().sendPacket(new MinecraftServerIssuePacket(this.instance.getClientUUID(), server.getServerName(), MinecraftServerIssuePacket.Type.MAKE));
        }
    }

    public void patchServer(MinecraftServerC server, File serverPath, boolean isCoupaingServer)
    {
        try
        {
            this.instance.getLinuxBridge().sed("%serverName%", server.getServerName(), new File(serverPath, "plugins" + File.separator + "SamaGamesAPI" + File.separator + "config.yml").getAbsolutePath());
            this.instance.getLinuxBridge().sed("%serverPort%", String.valueOf(server.getPort()), new File(serverPath, "server.properties").getAbsolutePath());
            this.instance.getLinuxBridge().sed("%serverIp%", InternetUtils.getExternalIp(), new File(serverPath, "server.properties").getAbsolutePath());
            this.instance.getLinuxBridge().sed("%serverName%", server.getServerName(), new File(serverPath, "scripts.txt").getAbsolutePath());

            if(isCoupaingServer)
            {
                File coupaingFile = new File(serverPath, "plugins/coupaing.json");
                coupaingFile.createNewFile();

                JsonObject rootJson = new JsonObject();
                rootJson.addProperty("min-slot", server.getMinSlot());
                rootJson.addProperty("max-slot", server.getMaxSlot());

                HashMap<String, String> options = server.getOptions();

                for(String key : options.keySet())
                    rootJson.addProperty(key, options.get(key));

                FileOutputStream fOut = new FileOutputStream(coupaingFile);
                OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
                myOutWriter.append(new Gson().toJson(rootJson));
                myOutWriter.close();
                fOut.close();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            instance.getConnectionManager().sendPacket(new MinecraftServerIssuePacket(this.instance.getClientUUID(), server.getServerName(), MinecraftServerIssuePacket.Type.PATCH));
        }
    }
}