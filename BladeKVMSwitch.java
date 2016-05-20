package smc.ext;

import com.supermicro.ipmi.IPMIInterfaceConfig;
import com.supermicro.ipmi.IPMIMessagingCommand;
import com.supermicro.ipmi.IPMIGlobalCommand;
import com.supermicro.ipmi.blade.BladeSystemEntity;
import com.supermicro.ipmi.blade.BladeEntity;
import com.supermicro.ipmi.ISessionController;
import com.supermicro.ipmi.IPMICMMOEMCommand;
import com.supermicro.ipmi.IPMIException;
import com.supermicro.ipmi.SessionControllerFactory;
import java.util.Timer;
import java.util.TimerTask;

/**
 * <p>Title: Responsible for Blade KVM switch when using softkey</p>
 *
 * <p>Description: a connection will be kept when using this object </p>
 *    should not save the current KVM position. becuase the KVM position may be modified by other AP or Blade Classis
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class BladeKVMSwitch {

    IPMICMMOEMCommand ipmiCMMOEMCommand = null;  //for switch kvm and get Blade info
    IPMIGlobalCommand ipmiGlobalCommand = null;  //for keep session
    ISessionController sessionController = null; //a connection will be kept when using this objec
    IPMIInterfaceConfig config;
    String bladeSize; //number of blade

    public BladeKVMSwitch(){

    }

    public BladeKVMSwitch(String ip,String id,String password) {
        init(ip,id,password);
    }

    /**
     * setup network connect to blade
     * @param ip String
     * @param id String
     * @param password String
     */
    public void init(String ip, String id, String password) {
        config = new IPMIInterfaceConfig();
        config.setIp(ip);
        config.setUserName(id);
        config.setPassword(password);

        createSession();
    }

    private void createSession(){
        //createSession
        ipmiCMMOEMCommand = new IPMICMMOEMCommand(null);
        ipmiGlobalCommand = new IPMIGlobalCommand(null);

        try {
            sessionController = SessionControllerFactory.createSessionControllerEx(config,ipmiCMMOEMCommand);
            ipmiGlobalCommand.setIPMIInterface(ipmiCMMOEMCommand.getIPMIInterface());
            keepSession(true);
        } catch (IPMIException ex) {
        }
    }


    Timer timer;
    private void keepSession(boolean flag){
        if(flag){
            TimerTask timerTask = new TimerTask() {
                public void run() {
                    ipmiGlobalCommand.getDeviceIDCommand();
                }
            };
            timer = new java.util.Timer();
            timer.schedule(timerTask,0,5000);
        }else{
            if(timer != null){
                timer.cancel();
                timer = null;
            }
        }
    }

    /**
     * close the connected session
     */
    public void closeConnection(){
        keepSession(false);
        if(sessionController != null){
            sessionController.closeSession();
        }
    }

    //check present
    public boolean isBladePresent(int index) {
        index--;

        BladeSystemEntity bladeSystem = getBladeSystemEntity();

        if(bladeSystem != null){

            if(index >= bladeSystem.getBlades().length){ //index out of blade size
                return false;
            }

            if(bladeSystem.getBlades()[index].getPresent() != 0){
                return true;
            }
        }
        return false;
    }

    public BladeSystemEntity getBladeSystemEntity(){
        if(ipmiCMMOEMCommand == null) return null;
        BladeSystemEntity bladeSystem = new BladeSystemEntity(config);
        bladeSystem.setEnableAllQuery(false);
        bladeSystem.setIsQueryBladeEntity(true);
        bladeSystem.setIfCheckOnlyOneTime(true); //that mean don't check "IfCheckOnlyOneTime"
        bladeSystem.setUseExistConnection(true);  //have set to ture if we use exist connection
        bladeSystem.setIpmiCMMOEMCommand(ipmiCMMOEMCommand);
        bladeSystem.getData();
        if(bladeSystem.isLoginSuccessful){
           return bladeSystem;
        }
        return null;
    }

    /**
     * return number of blade in this blade system. it should be 10 or 14 currently. 2008/09/24
     * @return int
     */
    public int getBladeSize() throws Exception{
        //call system GUID to determine the size of blade module
        byte[] guid = IPMIMessagingCommand.getSystemGUIDByIP(config.getIp());

        if (guid != null && IPMIMessagingCommand.isCMMGUID(guid)) {
            //0-7 is guid, 8 - SB, 9 - CMM, 10-PS, 11-IB, 12-GB
            int bladeSize = guid[8];
            int cmmModuleSize = guid[9];
            int powerSupplySize = guid[10];
            int infiniBandSize = guid[11];
            int gigabitSwitchSize = guid[12];
            return bladeSize;
        }
        return 0;
    }

    /**
     * current KVM position
     * @return int
     */
    public CurrentAndNextKVM getCurrentAndNextKVM() throws Exception{
        CurrentAndNextKVM c = new CurrentAndNextKVM();

        BladeSystemEntity bladeSystem = new BladeSystemEntity(config);
        bladeSystem.setEnableAllQuery(false);
        bladeSystem.setIsQueryBladeEntity(true);
        bladeSystem.setIfCheckOnlyOneTime(true); //that mean don't check "IfCheckOnlyOneTime"
        bladeSystem.setUseExistConnection(true);  //have set to ture if we use exist connection
        bladeSystem.setIpmiCMMOEMCommand(ipmiCMMOEMCommand);
        bladeSystem.getData();

        //cal current position
        for (int i = 0; i < bladeSystem.getBlades().length; i++) {
            BladeEntity blade = bladeSystem.getBlades()[i];
            if (blade.getPresent() > 0 && blade.getKvmEnable() == (byte) 0x01) {
                c.current = i + 1;
                break;
            }
        }

        //cannot find any kvm enabled. a special case
        if(c.current == 0){
            for (int i = 0; i < bladeSystem.getBlades().length; i++) {
                BladeEntity blade = bladeSystem.getBlades()[i];
                if (blade.getPresent() > 0) {
                    c.current = i + 1;
                    c.next     = i + 1;
                    c.previous = i + 1;
                    return c;
                }
            }
            //cannot find any blade presented. a special case
            throw new Exception("error. no Blade presents");
        }

        //cal next
        int index = c.current-1;
        do{
            index++;
            if(index == 10) index = 0;
            BladeEntity blade = bladeSystem.getBlades()[index];
            if(blade.getPresent() > 0){
               c.next = index+1;
               break;
           }
        }while(index != c.current-1);

        //cal previous
        index = c.current-1;
        do{
            index--;
            if(index < 0) index = bladeSystem.getBlades().length -1 ;
            BladeEntity blade = bladeSystem.getBlades()[index];
            if(blade.getPresent() > 0){
               c.previous = index+1;
               break;
           }
        }while(index != c.current-1);

        return c;
    }

    /**
     * switch KVM to position index.
     *
     * @param index int (1 based)
     */
    public void requestKVM(int index) throws Exception{
        ipmiCMMOEMCommand.requestKVM((byte)index);
    }

    /**
     * switch to previous blade. ie, current kvm screen is blade 3. then change to 2
     */
    public int switchToLeft() throws Exception{
        CurrentAndNextKVM c = getCurrentAndNextKVM();
        int bladeSize = getBladeSize();

        if (c == null){
            throw new Exception("error");
        }
        //System.out.println("p:"+ c.previous+ "c:"+ c.current + "n:" +c.next);
        requestKVM(c.previous);
        return c.previous;

    }

    /**
     * switch to next blade. ie, current kvm screen is blade 3. then change to 4
     */
    public int switchToRight() throws Exception{
        CurrentAndNextKVM c = getCurrentAndNextKVM();
        int bladeSize = getBladeSize();

        if (c == null){
            throw new Exception("error");
        }

        //System.out.println("p:"+ c.previous+ "c:"+ c.current + "n:" +c.next);
        requestKVM(c.next);
        return c.next;
    }

    /**
     * store current and next index of KVM
     * <p>Title: </p>
     *
     * <p>Description: </p>
     *
     * <p>Copyright: Copyright (c) 2006</p>
     *
     * <p>Company: </p>
     *
     * @author not attributable
     * @version 1.0
     */
    public class CurrentAndNextKVM{
        //all 1-based
        public int current;
        public int next;
        public int previous;
    }



}
