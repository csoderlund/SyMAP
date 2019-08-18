package props;

public interface PersistentProps {
    public PersistentProps copy(String name);
    public boolean equals(Object obj);
    public String getName();
    public void setName(String name);
    public String getProp();
    public void setProp(String value);
    public void deleteProp();
}
