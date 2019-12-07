package com.SyntaxAnalysis;

import com.Production.Production;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Stack;

public class SyntaxAnalysisLL1
{
    //所有的产生式
    private ArrayList<Production> ProductionList;
    //产生式中所有非终结符
    private ArrayList<Character>NonTerminals;
    //产生式中所有终结符
    private ArrayList<Character>Terminals;
    //编号好的产生式
    private HashMap<Integer,Production> OrderedProductionsMap;
    //First集
    private HashMap<Character,ArrayList<Character>>FirstSet;
    //Follow集
    private HashMap<Character,ArrayList<Character>>FollowSet;
    //预测分析表
    private HashMap<HashMap<Character,Character>,Production>PredictionTable;
    //用于记录给定字符串与产生式作用时日志
    private StringBuffer LoggingInfo;

    //最上层的非终结符
    private Character FirstNonTerminator = null;

    //产生式语法文件路径
    private final String grammarFilePath;

    /*
    * 构造函数，传入产生式文本路径
    * */
    public SyntaxAnalysisLL1(String filePath)
    {
        ProductionList = new ArrayList<>();
        NonTerminals =new ArrayList<>();
        Terminals = new ArrayList<>();
        OrderedProductionsMap = new HashMap<>();
        FirstSet = new HashMap<>();
        FollowSet = new HashMap<>();
        PredictionTable = new HashMap<>();
        LoggingInfo = new StringBuffer();
        grammarFilePath = filePath;
    }

    /*
    * 从文本中获取初始的产生式
    * */
    private void getProduction()
    {
        try
        {
            Scanner sc = new Scanner(new File(grammarFilePath));
            while (sc.hasNextLine())
            {
                String oneLineGrammar = sc.nextLine();
                oneLineGrammar = oneLineGrammar.replace(" ","");//去除该行所有的空格

                String[] split = oneLineGrammar.split("->");
                if (split.length!=2)continue;

                Character left = split[0].charAt(0);
                if(split[1].contains("|"))
                {
                    String[] rights = split[1].split("\\|");
                    for (String right:rights)
                    {
                        ProductionList.add(new Production(left,right));
                    }
                }
                else
                {
                    ProductionList.add(new Production(left,split[1]));
                }
            }
            get_Terminals_NonTerminals_FromProduction();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /*
    * 从产生式中获得终结符和非终结符
    * */
    private void get_Terminals_NonTerminals_FromProduction()
    {
        for (Production production : ProductionList)
        {
            Character left = production.getLeft();
            String rightStr = production.getRight();
            if (!NonTerminals.contains(left))
            {
                NonTerminals.add(left);
                if (FirstNonTerminator == null)
                {
                    FirstNonTerminator = left;
                }
            }
            for (Character ch : rightStr.toCharArray())
            {
                if (isTerminator(ch) && !Terminals.contains(ch))
                {
                    Terminals.add(ch);
                }
            }
        }
    }

    /*
    * 消除左公因子
    * */
    private void extract_Common_Factor()
    {
        for (int i = 0;i<ProductionList.size();i++)
        {
            Character left = ProductionList.get(i).getLeft();
            char factor = ProductionList.get(i).getRight().charAt(0);
            ArrayList<Integer>SameLeftFactorList = getSameLeftFactor(i,left,factor);
            if (SameLeftFactorList.size() != 0)
            {
                ArrayList<String> remainingRightList = new ArrayList<>();
                ArrayList<Production>shouldBeRemovedProductions = new ArrayList<>();
                for (Integer id:SameLeftFactorList)
                {
                    String remainingRightStr = ProductionList.get(id).getRight().length()==1 ? "ε" : ProductionList.get(id).getRight().substring(1);
                    remainingRightList.add(remainingRightStr);
                    shouldBeRemovedProductions.add(ProductionList.get(id));
                }

                //还要加上自己
                String remainingRightStr =  ProductionList.get(i).getRight().length()==1 ? "ε" : ProductionList.get(i).getRight().substring(1);
                remainingRightList.add(remainingRightStr);
                shouldBeRemovedProductions.add(ProductionList.get(i));

                for (var shouldBeRemovedProduction:shouldBeRemovedProductions)
                {
                    ProductionList.remove(shouldBeRemovedProduction);
                }
                Character NewNonTerminal = getNewNonTerminal();
                NonTerminals.add(NewNonTerminal);

                if (NewNonTerminal == null)
                {
                    System.out.println("无更多的非终结符");
                    return;
                }

                ProductionList.add(new Production(left,""+factor+NewNonTerminal));
                for (String str:remainingRightList)
                {
                    Production tmpProduction = new Production(NewNonTerminal,str);
                    if (ProductionList.contains(tmpProduction))continue;
                    ProductionList.add(tmpProduction);
                }
                i=-1;
            }
        }

    }

    /*
    * 获取与给定的产生式具有相同的左公因子的产生式
    * */
    private ArrayList<Integer> getSameLeftFactor(Integer num,Character left,char factor)
    {
        ArrayList<Integer> HasSameLeftFactorList = new ArrayList<>();
        for (int i =0;i<ProductionList.size();i++)
        {
            char ch = ProductionList.get(i).getRight().charAt(0);
            Character curLeft = ProductionList.get(i).getLeft();
            if (num != i && isTerminator(ch) && left.equals(curLeft) && ch == factor)
            {
                HasSameLeftFactorList.add(i);
            }
        }
        return HasSameLeftFactorList;
    }

    /*
    * 获取新的非终结符(即没有出现在产生式中的终结符)
    * */
    private Character getNewNonTerminal()
    {
        String str = "QAZWSXEDCRFVTGBYHNUJMIKOLP";
        for (char ch:str.toCharArray())
        {
            if (!NonTerminals.contains(ch))
            {
                return ch;
            }
        }
        return null;
    }

    /*
    * 消除直接左递归
    * */
    private void eliminate_Directly_Left_Recursive()
    {
        for (int i=0;i<ProductionList.size();i++)
        {
            Production production = ProductionList.get(i);
            if (isLeftRecursive(production))
            {
                ArrayList<Production>SameleftProductionList = findSameLeftProduction(production);
                ArrayList<Production> SameleftAndRightIsTerminalProduction = findSameLeftAndRightIsTerminalProduction(production);
                if (SameleftAndRightIsTerminalProduction.size() == 0)
                {
                    System.out.println("无法使用LL1解析产生式，因为无法消除产生式的左递归");
                    return;
                }
                ArrayList<String>remainingRightList = new ArrayList<>();
                ArrayList<String>rightIsTerminalList = new ArrayList<>();

                for (Production curProduction:SameleftAndRightIsTerminalProduction)
                {
                    rightIsTerminalList.add(curProduction.getRight());
                }

                Character newNonTerminal = getNewNonTerminal();//新的非终结符
                NonTerminals.add(newNonTerminal);

                Character curNonTerminal = production.getLeft();//该条产生式的左部
                for (Production curProduction:SameleftProductionList)
                {
                    String str = curProduction.getRight().length()>1 ? curProduction.getRight().substring(1) : "ε";
                    if (str.equals("ε") && remainingRightList.contains("ε"))continue;
                    remainingRightList.add(str);
                }
                //只需要删除既有相同左部的产生式，右部为非终结符和右部为非终结符的都包含在其中
                for (Production curProduction:SameleftProductionList)
                {
                    ProductionList.remove(curProduction);
                }

                for (String str:rightIsTerminalList)
                {
                    ProductionList.add(new Production(curNonTerminal,str+newNonTerminal));
                }

                for (String str:remainingRightList)
                {
                    if (str.equals("ε"))
                    {
                        ProductionList.add(new Production(newNonTerminal,str));
                        continue;
                    }
                    ProductionList.add(new Production(newNonTerminal,str+newNonTerminal));
                }
                i = -1;
            }
        }
    }

    /*
    * 判断产生式是否是直接左递归
    * */
    private boolean isLeftRecursive(Production production)
    {
        return isNonTerminator(production.getLeft()) && production.getLeft().equals(production.getRight().charAt(0));
    }

    /*
    * 找到与给定产生式具有相同的左部的产生式List
    * */
    private ArrayList<Production> findSameLeftProduction(Production curProduction)
    {
        ArrayList<Production>SameLeftProductionList = new ArrayList<>();
        for (Production production:ProductionList)
        {
            if (production.getLeft().equals(curProduction.getLeft()))
            {
                SameLeftProductionList.add(production);
            }
        }
        return SameLeftProductionList;
    }

    /*
    * 找到与给定的产生式具有相同左部的且右部第一个字符为终结符的产生式List
    * */
    private ArrayList<Production> findSameLeftAndRightIsTerminalProduction(Production curProduction)
    {
        ArrayList<Production>SameLeftAndRightIsTerminalProductionList = new ArrayList<>();
        for (Production production:ProductionList)
        {
            if (isTerminator(production.getRight().charAt(0)) && production.getLeft().equals(curProduction.getLeft()))
            {
                SameLeftAndRightIsTerminalProductionList.add(production);
            }
        }
        return SameLeftAndRightIsTerminalProductionList;
    }

//    /*
//    * 用于消除间接左递归
//    * */
//    private void eliminate_Indirect_Left_Recursive()
//    {
//
//
//    }
//
//    private boolean isInDirectLeftRecursive(Production production)
//    {
//        for (int i=0;i<production.getRight().length();i++)
//        {
//            char ch = production.getRight().charAt(i);
//            if (isNonTerminator(ch))
//            {
//
//            }
//        }
//        return true;
//    }

    /*
    * 给无序的产生式编号
    * */
    private void MakeProductionsOrdered()
    {
        int Number = 0;
        for (Production production : ProductionList)
        {
            OrderedProductionsMap.put(Number++,production);
        }
    }

    /*
    * 用于计算所有非终结符的First集合
    * */
    private void calculateFirstSet()
    {
        for (Character NonTerminator:NonTerminals)
        {
            FirstSet.put(NonTerminator,null);
        }
        while (!isAllNonTerminatorHasFirstSet())
        {
            for (Character NonTerminator:NonTerminals)
            {
                if (FirstSet.get(NonTerminator) != null)continue;//避免重复计算
                FirstSet.put(NonTerminator,calculateFirstSetByNonTerminator(NonTerminator));
            }
        }
    }

    /*
    * 找到给定的非终结符的First集
    * */
    private ArrayList<Character> calculateFirstSetByNonTerminator(char NonTerminator)
    {
        if (!isNonTerminator(NonTerminator))
        {
            System.out.println("给定元素不是非终结符，无法求得First集");
            return null;
        }
        ArrayList<Character> curFirstSet = new ArrayList<>();
        ArrayList<Production>sameLeftProductionList = getProductionByNonTerminator(NonTerminator);
        for (Production production:sameLeftProductionList)
        {
            char ch = production.getRight().charAt(0);
            //Situation 1: X -> aY...
            if (isTerminator(ch))   //如果左侧第一个字符为终结符，则直接加到First集里面
            {
                curFirstSet.add(ch);
            }
            else if (isNonTerminator(ch))   //如果左侧第一个字符为非终结符，则取该终结符的First集
            {
                ArrayList<Character> its_firstSet = FirstSet.get(ch);

                //如果该终结符的First集还未求得，则先暂缓当前非终结符求First集的进度，先求之后非终结符的First集
                if (its_firstSet == null)
                {
                    return null;
                }
                //如果该终结符的First集已经求得，则直接将该终结符得First集加入到当前终结符的First集里面
                else
                {
                    //Situation 2: X -> Y...
                    if (!its_firstSet.contains('ε')||production.getRight().length() == 1)
                    {
                        curFirstSet.addAll(its_firstSet);
                    }
                    //Situation 3: X -> YZ...(FirstSet(Y) contains 'ε')
                    else
                    {
                        curFirstSet.addAll(its_firstSet);
                        curFirstSet.remove(Character.valueOf('ε'));
                        ArrayList<Character> tmpFirstList = getFirstElementsOfProduction(new Production(NonTerminator,production.getRight().substring(1)));
                        if (tmpFirstList == null) return null;//说明某个非终结符的First还未计算
                        else
                        {
                            curFirstSet.addAll(tmpFirstList);
                        }
                    }
                }
            }
        }
        return curFirstSet;
    }

    /*
    *给定一个产生式，获取其First集(产生式左部为某一终结符)
    * */
    private ArrayList<Character> getFirstElementsOfProduction(Production production)
    {
        String right = production.getRight();
        ArrayList<Character> FirstElementsOfProduction = new ArrayList<>();
        if (isTerminator(right.charAt(0)))
        {
            FirstElementsOfProduction.add(right.charAt(0));
        }
        else if (isNonTerminator(right.charAt(0)))
        {
            ArrayList<Character>curFirstSet = FirstSet.get(right.charAt(0));
            if (curFirstSet == null)//还未求得该非终结符的First集
            {
                return null;
            }
            else
            {
                for (Character ch:curFirstSet)
                {
                    if (!ch.equals('ε')||right.length()==1) FirstElementsOfProduction.add(ch);
                    else
                    {
                        //递归获取First集
                        Character left = production.getLeft();
                        ArrayList<Character>tmpList = getFirstElementsOfProduction(new Production(left,right.substring(1)));
                        if (tmpList != null)
                            FirstElementsOfProduction.addAll(tmpList);
                    }
                }
            }
        }
        return FirstElementsOfProduction;
    }

    /*
    * 通过一个非终结符，找出所有当该终结符在产生式左侧的产生式
    * */
    private ArrayList<Production> getProductionByNonTerminator(Character NonTerminator)
    {
        ArrayList<Production>sameLeftProductionList = new ArrayList<>();
        for (Production production:ProductionList)
        {
            if (production.getLeft().equals(NonTerminator))
            {
                sameLeftProductionList.add(production);
            }
        }
        return sameLeftProductionList;
    }

    /*
    * 判断是否所有的非终结符都找到First集
    * */
    private boolean isAllNonTerminatorHasFirstSet()
    {
        boolean flag = true;
        for (Character NonTerminator:FirstSet.keySet())
        {
            ArrayList<Character>curFirstSet = FirstSet.get(NonTerminator);
            /*对于每个非终结符，如果已经求过了它的First集，则curFirstSet不会等于null，至少为空集
            如果curFirst为空，则说明可能在求该终结符的First集的过程中遇见要求另一个非终结符的First集，
            则先停止求当前终结符的First集，将其First集设置为null;或者还没有求该终结符的First集，也将它的First集设置为null
             */
            flag = flag && (curFirstSet != null);
        }
        return flag;
    }

    /*
    * 用于计算产生式的Follow集合
    * */
    private void calculateFollowSet()
    {
        for (Character NonTerminator : NonTerminals)
        {
            FollowSet.put(NonTerminator,new ArrayList<>());
        }
        HashMap<Character,ArrayList<Character>>TmpFollowSet;
        do
        {
            TmpFollowSet  = SyntaxAnalysisLL1.myClone(FollowSet);   //这里需要深拷贝
            for (Character NonTerminator : NonTerminals)
            {
                var tmpFollowList = calculateFollowSetByNonTerminator(NonTerminator);
                var FollowList = FollowSet.get(NonTerminator);
                if (tmpFollowList != null)
                {
                    for (Character followElement:tmpFollowList)
                    {
                        if (!FollowList.contains(followElement))
                        {
                            FollowList.add(followElement);
                        }
                    }
                    FollowSet.put(NonTerminator,FollowList);
                }
            }
        }while (!HasAllFollowSetOfNonTerminatorsNotChanged(TmpFollowSet,FollowSet));
    }

    /*
    * 通过终结符计算其Follow集
    * */
    private ArrayList<Character> calculateFollowSetByNonTerminator(char NonTerminator)
    {
        if (!isNonTerminator(NonTerminator))
        {
            System.out.println("给定元素不是非终结符，无法求得Follow集");
            return null;
        }
        ArrayList<Character> curFollowSet = new ArrayList<>();
        ArrayList<Production> curProductionList = getProductionByNonTerminator_2(NonTerminator);
        for (Production production:curProductionList)
        {
            String right = production.getRight();
            Character left = production.getLeft();
            int index = right.indexOf(NonTerminator);
            if (index == right.length()-1)  //Situation 1：该非终结符在产生式右部的最后
            {
                ArrayList<Character> its_FollowSet = FollowSet.get(left);

                /*如果左部的非终结符是自己，跳过该条产生式,
                  如果左部的非终结符的Follow为空，则先暂停该非终结符求Follow集的过程，
                  如果上述条件不成立，则将它的Follow集加到自己的Follow集里面
                */
                if (left != NonTerminator && its_FollowSet.size() != 0)
                {
                    curFollowSet.addAll(its_FollowSet);
                }
            }
            else if (isTerminator(right.charAt(index+1)))   //Situation 2：该非终结符后一个字符为终结符
            {
                curFollowSet.add(right.charAt(index+1));
            }
            //Situation 3：该非终结符后一个字符为非终结符，这该非终结符的Follow集为后面字符串的First集
            else if (isNonTerminator(right.charAt(index+1)))
            {
                ArrayList<Character>its_FirstSet = getFirstElementsOfProduction(new Production(left,right.substring(index+1)));
                if (its_FirstSet == null)continue;
                else if(its_FirstSet.contains('ε')) //如果后面的非终结符的First集还包含‘ε’，这除了要加它的First集以外，还要加它的Follow集，最后还要移除‘ε’
                {
                    ArrayList<Character>its_Follow = FollowSet.get(right.charAt(index+1));
                    curFollowSet.addAll(its_FirstSet);
                    if (its_Follow.size() != 0) curFollowSet.addAll(its_Follow);
                    curFollowSet.remove(Character.valueOf('ε'));    //Follow集里面不能有‘ε’
                }
                else curFollowSet.addAll(its_FirstSet);
            }
        }
        if (NonTerminator == this.FirstNonTerminator && !curFollowSet.contains('$'))
        {
            curFollowSet.add('$');
        }
        return curFollowSet;
    }

    /*
    * 判断是否所有非终结符都计算了Follow集
    * */
    private boolean HasAllFollowSetOfNonTerminatorsNotChanged(HashMap<Character,ArrayList<Character>>oldFollowSet,HashMap<Character,ArrayList<Character>>newFollowSet)
    {
        boolean flag = true;
        for (Character key:oldFollowSet.keySet())
        {
            var oldFollowList = oldFollowSet.get(key);
            var newFollowList = newFollowSet.get(key);
            for (var _key:newFollowList)
            {
                flag = flag && oldFollowList.contains(_key);
                if (!flag)return flag; //如果发现flag == false，则立即返回false
            }
        }
        return flag; //如果Flag == True，说明oldFollowSet 与 newFollowSet相等，则可以跳出循环了
    }

    /*
    * 给定非终结符，求出当该非终结符在产生式右边时的产生式
    * */
    private ArrayList<Production> getProductionByNonTerminator_2(Character NonTerminator)
    {
        ArrayList<Production> curProductionList = new ArrayList<>();
        for (Production production : ProductionList)
        {
            String right = production.getRight();
            if (right.contains(String.valueOf(NonTerminator)))
            {
                curProductionList.add(production);
            }
        }
        return curProductionList;
    }

    /*
    * 用于对外提供接口，测试给定的字符流是否符合产生式的规则
    * */
    public boolean canBeAccepted(String tokens)
    {
        ArrayList<Character>JudgingTerminals = Terminals;
        JudgingTerminals.add('$');

        int count = 0;//记录日志编号
        boolean flag = true;
        tokens+='$';
        Stack<Character>LL1Stack = new Stack<>();
        LL1Stack.push('$');
        LL1Stack.push(FirstNonTerminator);
        LoggingInfo.append("No.").append(count++).append(",The First Element of String is ").append(tokens.charAt(0))
        .append(",and the First NonTerminator on the top of Stack is ").append(LL1Stack.peek()).append("\n");

        for(char element : tokens.toCharArray())
        {
            if (!JudgingTerminals.contains(element))
            {
                LoggingInfo.append("No.").append(count)
                        .append(",find unknown symbol:").append(element)
                        .append("\n");

                System.out.println("未知符号："+element);
                return false;
            }
            Character topElement = LL1Stack.peek();
            if (isTerminator(topElement))
            {
                if (topElement.equals(element))
                {
                    LL1Stack.pop();
                    LoggingInfo.append("No.").append(count++)
                            .append(",Element: ").append(topElement)
                            .append(" Can be Recognized\n");
                }
                else
                {
                    LoggingInfo.append("No.").append(count)
                            .append(",Element: ").append(topElement)
                            .append(" Can not be Recognized\n");

                    return false;
                }
            }
            else
            {
                boolean canOut = false;
                while (!canOut)
                {
                    LL1Stack.pop();
                    Production predictedProduction = getProductionWhenMeetTerminator(topElement,element);
                    if (predictedProduction == null)        //无产生式来替换说明发生错误
                    {
                        LoggingInfo.append("No.").append(count)
                                .append(",Find Error with ").append(element)
                                .append(" Element\n");

                        System.out.println(element+"处存在错误");
                        return false;
                    }
                    LoggingInfo.append("No.").append(count++).append(",When NonTerminator: ").append(topElement)
                            .append(" meet Terminator：").append(element).append("，Using Production：")
                            .append(predictedProduction.toString()).append(" to replace\n");

                    char[]elementArray = predictedProduction.getRight().toCharArray();
                    if (!(elementArray.length == 1 && elementArray[0] == 'ε')) //当用 'ε'来替换时，不进行压栈操作
                    {
                        for (int i = elementArray.length - 1; i >= 0; i--)        //反序入栈
                        {
                            LL1Stack.push(elementArray[i]);
                        }
                    }
                    topElement = LL1Stack.peek();
                    if (isTerminator(topElement))
                    {
                        if (topElement.equals(element))
                        {
                            LL1Stack.pop();
                            LoggingInfo.append("No.").append(count++)
                                    .append(",Element: ").append(topElement)
                                    .append(" Can be Recognized\n");

                            canOut = true;
                        }
                        else
                        {
                            System.out.println(element+"处存在错误");
                            LoggingInfo.append("No.").append(count)
                                .append(",Find Error with ").append(element)
                                .append(" Element\n");
                            return false;
                        }
                    }
                }
            }
        }
        return flag;
    }

    /*
    * 当一非终结符遇见一终结符时返回相应的产生式
    * */
    private Production getProductionWhenMeetTerminator(Character NonTerminator, Character Terminator)
    {
        HashMap<Character,Character>Pairing = new HashMap<>();
        Pairing.put(NonTerminator,Terminator);
        return PredictionTable.get(Pairing);
    }

    /*
    * 用于打印规范后的产生式等
    * */
    public void printGrammarRules()
    {
        getProduction();//获取原始产生式
        extract_Common_Factor();//消除公因子
        eliminate_Directly_Left_Recursive();//消除直接左递归

        System.out.println("以下输出的是有序的产生式(经过消除公因子、消除左递归)");
        MakeProductionsOrdered();//将所有规范后的产生式编号
        OrderedProductionsMap.forEach((num, value) -> System.out.println("No: "+num+", Production: "+value));

        calculateFirstSet();//计算First集
        System.out.println("以下输出的是First集");
        FirstSet.forEach((NonTerminator,FirstList) -> System.out.println("非终结符："+NonTerminator+"的First集为："+FirstList));

        calculateFollowSet();//计算Follow集
        System.out.println("以下输出的是Follow集");
        FollowSet.forEach((NonTerminator,FollowList) -> System.out.println("非终结符："+NonTerminator+"的Follow集为："+FollowList));

        initPredictionTable();//初始化预测分析表
        makePredictionTable();//构建预测分析表

        System.out.println("以下输出的是预测分析表");
        for (var key:PredictionTable.keySet())
        {
            for (var _key:key.keySet())
            {
                System.out.print("当"+_key+"遇见"+key.get(_key)+"时，使用产生式：");
            }
            System.out.println(PredictionTable.get(key)==null?"":PredictionTable.get(key).toString());
        }
    }

    /*
    * 打印自顶向下分析的过程的日志
    * */
    public void printLoggingInfo()
    {
        System.out.println(LoggingInfo.toString());
    }

    /*
    * 初始化预测分析表
    * */
    private void initPredictionTable()
    {
        for (Character NonTerminator : NonTerminals)
        {
            for (Character Terminator : Terminals)
            {
                HashMap<Character,Character>Pairing = new HashMap<>();
                Pairing.put(NonTerminator,Terminator);
                PredictionTable.put(Pairing,null);
            }
        }
    }

    /*
    * 构建预测分析表
    * */
    private void makePredictionTable()
    {
        for (Character NonTerminator : NonTerminals)
        {
            ArrayList<Production> curProductionList = getProductionByNonTerminator(NonTerminator);
//            ArrayList<Character> its_FirstSet = FirstSet.get(NonTerminator);
            ArrayList<Character> its_FollowSet = FollowSet.get(NonTerminator);
            for (Production production:curProductionList)
            {
                ArrayList<Character> curFirstSet = getFirstElementsOfProduction(production);
                if (curFirstSet == null)
                {
                    System.out.println("无法找到该产生式右部的First集");
                    return;
                }
                for (Character Terminator : curFirstSet)
                {
                    if (Terminator.equals('ε')) continue;
                    HashMap<Character,Character> Pairing = new HashMap<>();
                    Pairing.put(NonTerminator,Terminator);
                    PredictionTable.put(Pairing,production);
                }
                if (curFirstSet.contains('ε'))
                {
                    for (Character Terminator : its_FollowSet)
                    {
                        HashMap<Character,Character> Pairing = new HashMap<>();
                        Pairing.put(NonTerminator,Terminator);
                        PredictionTable.put(Pairing,new Production(production.getLeft(),"ε"));//这个产生式肯定存在，这里是不过是使用快捷方式得到它
                    }
                }
            }

        }
    }

    /*
    * 判断给定的字符是否是终结
    * */
    private boolean isTerminator(char ch)
    {
        //所有的小写字母和部分其他字符是终结符
        return "!@^{}[]$%+-*/()ε#qazwsxedcrfvtgbyhnujmiklop".contains(String.valueOf(ch));
    }

    /*
    * 判断给定的字符是否是非终结符
    * */
    private boolean isNonTerminator(char ch)
    {
        //大写字母均为非终结符
        return "QAZWSXEDCRFVTGBYHNUJMIKOLP".contains(String.valueOf(ch));
    }

    /*
    * Follow集深拷贝函数
    * */
    private static HashMap<Character,ArrayList<Character>> myClone(HashMap<Character, ArrayList<Character>> obj)
    {
        HashMap<Character,ArrayList<Character>>myObj = new HashMap<>();
        for (Character key:obj.keySet())
        {
            ArrayList<Character> list = obj.get(key);
            ArrayList<Character> myList = new ArrayList<>();
            for (Character character : list)
            {
                myList.add(new Character(character.charValue()));
            }
            myObj.put(key,myList);
        }
        return myObj;
    }

    /*
    * 调用Python脚本画出预测分析表
    * */
    public void savePredictionTableToPicture()
    {
        try
        {
            BufferedWriter writer = new BufferedWriter(new FileWriter("PredictionTable.txt"));
            StringBuffer tmp_Terminals = new StringBuffer();
            tmp_Terminals.append("Terminals:");
            for (var Terminator : Terminals)
            {
                tmp_Terminals.append(Terminator).append(' ');
            }
            tmp_Terminals.append("\n");
            writer.append(tmp_Terminals);
            StringBuffer tmp_NonTerminals = new StringBuffer();
            tmp_NonTerminals.append("NonTerminals:");
            for (var NonTerminator : NonTerminals)
            {
                tmp_NonTerminals.append(NonTerminator).append(" ");
            }
            tmp_NonTerminals.append("\n");
            writer.append(tmp_NonTerminals);

            for (var key : PredictionTable.keySet()) //HashMap<HashMap<Character,Character>,Production>
            {
                StringBuffer _key = new StringBuffer();     //Character->Character
                StringBuilder left = new StringBuilder();   //Character
                StringBuilder right = new StringBuilder();  //Character
                for (var __key : key.keySet())  //HashMap<Character,Character>
                {
                    left.append(__key);
                    right.append(key.get(__key));
                }
                _key.append(left).append(" -> ").append(right);
                String value = PredictionTable.get(key) == null ? "" : PredictionTable.get(key).toString();
                writer.append(_key).append(" <-> ").append(value).append("\n");
                writer.flush();
            }
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        String script_path = System.getProperty("user.dir")+"\\makeImage.py";
        try
        {
            Process process = Runtime.getRuntime().exec("python "+script_path);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
